package com.yazuo.zeus.openapi.core.interceptor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

/**
 * 1.应用于接口参数,参数一定为非空,因为是按照参数的数据进行匹配是否对一个接口,相同请求请求了多次,
 * 非空会造成所有的请求都被认为是相同的请求。
 * 2.如果采用对象判定则一定要设置toString，否则对象只能按地址计算
 * 3.记住选取的参数一定能唯一标识请求是否重复（起码大概率上）
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface IdempotentVerification {

    /**
     * 幂等标识，根据入参中的参数进行查找，MD5后存入redis进行分布式控制
     * 如果这个为默认值则说明这个参数为基本类型包括String
     * 例：{"name","body.message"}
     * @return
     */
    String[] value() default {};
}
