package com.yazuo.zeus.openapi.core.interceptor;

import com.yazuo.common.yazuolog.YazuoLogger;
import com.yazuo.zeus.openapi.core.utils.JSONMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 监控服务接口
 */
@Aspect
@Component
public class WebRequestParamsAspect {

    private static final YazuoLogger logger=YazuoLogger.create(WebRequestParamsAspect.class);

    /**
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("execution(* com.yazuo.zeus.openapi.*.controller..*.*(..))")
    public Object methodExecutionArgs(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        Object[] params = joinPoint.getArgs();
        logger.info("method {}({})  请求参数(request) : {}", methodName, Thread.currentThread().getId(), JSONMapper.toJSONString(params));
        long start = System.currentTimeMillis();
        Object obj = joinPoint.proceed();
        long consumeTime = System.currentTimeMillis()-start;
        logger.info("method {}({})  响应结果(response) : {}  耗时(consume time) : {} ms", methodName, Thread.currentThread().getId(), JSONMapper.toJSONString(obj),consumeTime);
        return obj;
    }
}
