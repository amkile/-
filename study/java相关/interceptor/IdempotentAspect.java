package com.yazuo.zeus.openapi.core.interceptor;

import com.yazuo.baikal.cache.client.api.PrivateCacheClient;
import com.yazuo.zeus.openapi.core.exception.IdempotentException;
import com.yazuo.zeus.openapi.core.interceptor.annotation.Idempotent;
import com.yazuo.zeus.openapi.core.interceptor.annotation.IdempotentVerification;
import com.yazuo.zeus.openapi.core.lock.RedisLock;
import com.yazuo.zeus.openapi.core.utils.MD5Util;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import redis.clients.jedis.JedisCommands;

import javax.annotation.Resource;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 1.AOP扫描加注解配合完成重复提交问题,要注意的一点是会抛出两个关键的异常:
 * 一个是重复异常,一个是参数空指针异常
 * 2.其他异常:反射中的方法不存在和属性不存在的异常
 * @Author: yangguolong
 * @Description:
 * @Date: Create in 12:59 2019/6/11
 */
public class IdempotentAspect {

    @Autowired
    @Qualifier("privateCacheClient")
    private JedisCommands JedisCommands;

    @Resource
    private RedisLock redisLock;


    /**
     * 入口，环绕切片
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        idempotentVerification(joinPoint);
        return joinPoint.proceed();
    }

    private Class<?>[] getClass(Object[] objs) {
        Class<?>[] argTypes = new Class[objs.length];
        for (int i = 0; i < argTypes.length; i++) {
            argTypes[i] = objs[i].getClass();
        }
        return  argTypes;
    }

    private IdempotentVerification[] getIdempotentVerification(Annotation[][] as) {
        IdempotentVerification[] ids = new IdempotentVerification[as.length];
        for (int i=0;i<as.length;i++) {
            for (Annotation a2:as[i]) {
                if (a2 instanceof IdempotentVerification) {
                    // hash映射，将位置信息映射为IdempotentVerification信息
                    ids[i] = (IdempotentVerification) a2;
                }
            }
        }
        return ids;
    }


    /**
     * 认证开始
     * @param joinPoint
     * @throws NoSuchMethodException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private void idempotentVerification(ProceedingJoinPoint joinPoint) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        Object aspectObject = joinPoint.getTarget();
        Class aspectClass = aspectObject.getClass();
        Object[] aspectArgs = joinPoint.getArgs();
        Class<?>[] argTypes = getClass(aspectArgs);
        Method aspectMethod = aspectClass.getDeclaredMethod(joinPoint.getSignature().getName(),argTypes);
        Idempotent idt = aspectMethod.getAnnotation(Idempotent.class);
        boolean b = execute(idt,aspectObject,aspectArgs,aspectMethod);
        if (!b) {
            throw new IdempotentException(idt.message());
        }
    }

    /**
     * 执行开始
     * @param idt
     * @param target
     * @param aspectArgs
     * @param method
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private boolean execute(Idempotent idt,Object target, Object[] aspectArgs,Method method) throws NoSuchFieldException, IllegalAccessException {
        if (idt != null) {
            String key = getIdempotentKey(target,aspectArgs,method);
            key = MD5Util.md5(key,"");
            String lockKey = getLockKey(target,method,idt);
            redisLock.lock(lockKey,idt.lockExpire());
            try {
                if (!isExists(key)) {
                    setReids(key,String.valueOf(System.currentTimeMillis()),idt.idempotentExpire());
                    return true;
                } else {
                    return false;
                }
            }finally {
                redisLock.unLock(lockKey);
            }
        }else {
            return true;
        }
    }

    /**
     * 分布式lock，Key值
     * @param target
     * @param method
     * @param id
     * @return
     */
    private String getLockKey(Object target,Method method,Idempotent id){
        return  target.getClass().getName()+"_"+method.getName()+"_"+id.lockKey();
    }

    /**
     * 幂等标识key
     * @param aspectArgs
     * @param method
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private String getIdempotentKey(Object target, Object[] aspectArgs,Method method) throws NoSuchFieldException, IllegalAccessException {
        // 以下的数组都为一一对应，无论是位置关系还是长度
        // 参数的注解，如果没有注解，第二维度为null，第一维度一定存在，第一维度长度为参数数量
        Annotation[][] annotations = method.getParameterAnnotations();
        // 参数的类型同上
        Class[] parameClass =  method.getParameterTypes();
        // 获取IdempotentVerification的位置关系
        IdempotentVerification[] ids = getIdempotentVerification(annotations);
        checkNull(ids);
        StringBuilder stringBuilder = new StringBuilder(target.getClass().getName()+method.getName());
        // 以参数实体数量为标准,此处的i为位置信息
        for (int i = 0;i<aspectArgs.length;i++) {
            // 如果参数未标注IdempotentVerification，此位置数据为null
            if (ids[i] != null) {
                // IdempotentVerification中的value值的长度决定标记是参数本身（String和基本类型），还是参数（对象）中的数据
                if (ids[i].value().length<=0) {
                    checkNull(aspectArgs[i],i,null);
                    stringBuilder.append(aspectArgs[i]);
                }else {
                    for (String name: ids[i].value()) {
                        // 以.解析分组
                        String[] ns = split(name);
                        // 临时存储参数
                        Class ct = parameClass[i];
                        Object ot = aspectArgs[i];
                        Object lastObj = ot;
                        for (String n:ns) {
                            lastObj = getObjectByName(ct,ot,n);
                            checkNull(lastObj,i,n);
                            ct = lastObj.getClass();
                            ot = lastObj;
                        }
                        stringBuilder.append(lastObj);
                    }
                }
            }
        }
        return stringBuilder.toString();
    }

    private boolean checkNull(Object[] os) {
        for (Object o1:os) {
            if (o1 != null) {
                return true;
            }
        }
        throw new NullPointerException("请添加注解IdempotentVerification");
    }

    private boolean checkNull(Object o,int index,String name) {
        name = name != null?",参数名称："+name:"";
        if (o == null) {
            throw new NullPointerException("参数位置："+index+name);
        }
        return true;
    }


    private String[] split(String str) {
        return str.split("\\.");
    }

    private Object getObjectByName(Class c,Object bo,String name ) throws NoSuchFieldException, IllegalAccessException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(bo);
    }

    private void setReids(String key,String value,int s) {
        JedisCommands.setex(key,s,value);
    }


    private boolean isExists(String key) {
        return JedisCommands.exists(key);
    }

}
