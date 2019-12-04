package com.fh;

import com.fh.controller.app.MyBpmnXMLConstants;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.javax.el.ExpressionFactory;
import org.activiti.engine.impl.javax.el.ValueExpression;
import org.activiti.engine.impl.juel.ExpressionFactoryImpl;
import org.activiti.engine.impl.juel.SimpleContext;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author ygl
 * @description 节点信息获取
 * @date 2019/11/16
 */
public class ActivitiUtil {

    protected static final ThreadLocal<Map<String, Object>> threadLocalCondition = new ThreadLocal<>();
    protected static final ThreadLocal<SimpleContext> threadLocalContext = new ThreadLocal<>();

    protected ProcessEngine processEngine;

    protected RepositoryService repositoryService;

    protected RuntimeService runtimeService;

    protected HistoryService historyService;

    protected TaskService taskService;

    public ActivitiUtil(ProcessEngine processEngine) {
        this.processEngine = processEngine;
        this.repositoryService = processEngine.getRepositoryService();
        this.runtimeService = processEngine.getRuntimeService();
        this.historyService = processEngine.getHistoryService();
        this.taskService = processEngine.getTaskService();
    }

    // 获取下一个节点
    public ActivityImpl getNextUserTask(ActivityImpl activity) {
        return getNextUserTask(activity, null);
    }

    // 获取下一个节点
    public ActivityImpl getNextUserTask(ActivityImpl activity, String procInstId) {
        if (activity == null || activity.getOutgoingTransitions().size() <= 0) {
            throw new RuntimeException("节点不存在");
        }
        List<PvmTransition> outTransitions = activity.getOutgoingTransitions();
        if (outTransitions.size() == 1) {
            // 单条路径
            PvmTransition pv = outTransitions.get(0);
            ActivityImpl ad = (ActivityImpl) pv.getDestination();
            return notGateway(ad, procInstId);
        } else if (outTransitions.size() > 1) {
            //默认选中
            ActivityImpl ad = (ActivityImpl) outTransitions.get(0).getDestination();
            return notGateway(ad, procInstId);
        }
        return activity;
    }

    // 排除多网关级联
    public ActivityImpl notGateway(ActivityImpl ad, String procInstId) {
        if (StringUtils.isEmpty(procInstId) && ad.getProperty(MyBpmnXMLConstants.ATTRIBUTE_TYPE).equals(MyBpmnXMLConstants.ELEMENT_GATEWAY_EXCLUSIVE)) {
            throw new RuntimeException("不支持的流程图设计,流程未开始,网关无法判定路径");
        }
        while (ad.getProperty(MyBpmnXMLConstants.ATTRIBUTE_TYPE).equals(MyBpmnXMLConstants.ELEMENT_GATEWAY_EXCLUSIVE)) {
            ad = gateway(ad, procInstId);
        }
        return ad;
    }

    // 排除单网关
    public ActivityImpl gateway(final ActivityImpl ad, String procInstId) {
        // 网关判定
        if (ad.getProperty(MyBpmnXMLConstants.ATTRIBUTE_TYPE).equals(MyBpmnXMLConstants.ELEMENT_GATEWAY_EXCLUSIVE)) {
            List<PvmTransition> gateWayPvs = ad.getOutgoingTransitions();
            if (threadLocalCondition.get() == null) {
                threadLocalCondition.set(getGatewayContext(procInstId));
            }
            for (PvmTransition gv : gateWayPvs) {
                if (isCondition((String) gv.getProperty(MyBpmnXMLConstants.CONDITION_TEXT), threadLocalCondition.get())) {
                    return (ActivityImpl) gv.getDestination();
                }
            }
        }
        return ad;
    }

    // 获取el表达式的上下文
    public Map<String, Object> getGatewayContext(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    // 网关下一步判断
    public boolean isCondition(String value, Map<String, Object> contextMap) {
        ExpressionFactory factory = new ExpressionFactoryImpl();
        if (threadLocalContext.get() == null) {
            SimpleContext context = new SimpleContext();
            for (Map.Entry<String, Object> e : contextMap.entrySet()) {
                context.setVariable(e.getKey(), factory.createValueExpression(e.getValue(), Object.class));
            }
            threadLocalContext.set(context);
        }

        ValueExpression e = factory.createValueExpression(threadLocalContext.get(), value, boolean.class);
        return (Boolean) e.getValue(threadLocalContext.get());
    }

    //返回当前节点或者是开始节点
    public ActivityImpl getTaskUserNode(String activityId,List<ActivityImpl> activities){
        for (ActivityImpl activity : activities) {
            Map m = activity.getProperties();
            // 找到第一个userNode节点
            if (activityId == null && m.get(MyBpmnXMLConstants.ATTRIBUTE_TYPE).equals(MyBpmnXMLConstants.ELEMENT_EVENT_START)) {
                return isUserTake((ActivityImpl) activity.getOutgoingTransitions().get(0).getDestination());
            } else if (activity.getId().equals(activityId)) {
                return activity;
            }

        }
        return null;
    }

    // 当前userTask节点或者开始节点
    public ActivityImpl getTaskUserNode(List<ActivityImpl> activities, String taskId) {
        //userTask1~n,exclusiveGateway,endEven,startEven
        String activityId = null;
        if (taskId != null) {
            activityId = getActivityIdByTaskId(taskId);
        }
        return getTaskUserNode(activityId,activities);
    }

    // 排除非userTask的节点
    private ActivityImpl isUserTake(ActivityImpl activity) {
        Map m = activity.getProperties();
        String type = m.get(MyBpmnXMLConstants.ATTRIBUTE_TYPE).toString();
        if (type.equals(MyBpmnXMLConstants.ELEMENT_TASK_USER) || type.equals(MyBpmnXMLConstants.ELEMENT_EVENT_END)) {
            return activity;
        }
        return isUserTake((ActivityImpl) activity.getOutgoingTransitions().get(0).getDestination());
    }

    // 当前userTask节点
    public ActivityImpl getTaskUserNodeStart(List<ActivityImpl> activities) {
        return getTaskUserNode(activities, null);
    }

    // 获取流程定义的所有节点信息
    public List<ActivityImpl> getActivityImpl(String defineId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().
                processDefinitionId(defineId).singleResult();
        ProcessDefinitionEntity processDefinitionEntity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definition.getId());
        return processDefinitionEntity.getActivities();
    }

    // 根据taskId获取节点id
    public String getActivityIdByTaskId(String taskId) {
        ExecutionQuery executionQuery = runtimeService.createExecutionQuery();
        TaskQuery taskQuery = taskService.createTaskQuery();
        Task task = taskQuery.taskId(taskId).singleResult();
        Execution execution = executionQuery.executionId(task.getExecutionId()).singleResult();
        return execution.getActivityId();
    }

    public String getActivityIdByHisTaskId(String taskId){
        HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        return task.getTaskDefinitionKey();
    }

    public boolean isEndParallelTaskUser(String taskId) {
        Integer nrOfInstances = (Integer) taskService.getVariable(taskId, "nrOfInstances");
        Integer nrOfCompletedInstances = (Integer) taskService.getVariable(taskId, "nrOfCompletedInstances");
        return  (nrOfCompletedInstances == null && nrOfInstances == null) ||  (nrOfCompletedInstances + 1)  / nrOfInstances >= 1;
    }

    public static boolean isEnd(ActivityImpl activity){
        return activity.getProperty(MyBpmnXMLConstants.ATTRIBUTE_TYPE).equals(MyBpmnXMLConstants.ELEMENT_EVENT_END);
    }

    public String[] getAssigneeBefore(List<ActivityImpl> activities,String taskId,String processInstId){
        List<HistoricTaskInstance> historicTaskInstances = historyService.createHistoricTaskInstanceQuery().processInstanceId(processInstId).orderByTaskCreateTime().desc().list();
        List<String> assignees = new ArrayList<>();
        String[] s = new String[0];
        if (historicTaskInstances.size()>=2) {
            int i = 0;
            HistoricTaskInstance curr = historicTaskInstances.get(i++);
            while (i<historicTaskInstances.size()&&historicTaskInstances.get(i).getTaskDefinitionKey().equals(curr.getTaskDefinitionKey())) i++;
            HistoricTaskInstance before = historicTaskInstances.get(i);
            while (i<historicTaskInstances.size()&&historicTaskInstances.get(i).getTaskDefinitionKey().equals(before.getTaskDefinitionKey())){
                // 被逼无奈的写法,因为之前的人没用activity的协作办理模式,导致了历史数据数据有问题
                for (String username : historicTaskInstances.get(i++).getAssignee().split(",")) {
                    if (StringUtils.isEmpty(username))continue;
                    assignees.add(username);
                }

            }
            s = new String[assignees.size()];
            for (i=0;i<assignees.size();i++) {
                s[i] = assignees.get(i);
            }

        }
        return s;
    }
}
