package com.yazuo.zeus.openapi.core.interceptor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 加上注解才能被扫描到
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Idempotent {

    String lockKey();
    /**
     * 毫秒
     * lock锁超时时间，默认一秒
     * @return
     */
    int lockExpire() default 1000;

    /**
     * 秒
     * 幂等标识存在时间，默认30秒
     * @return
     */
    int idempotentExpire() default 30;

    String message() default "操作太快啦，请30秒后再次操作~";
}
