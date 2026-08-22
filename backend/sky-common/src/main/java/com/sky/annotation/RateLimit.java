package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sky.enumeration.RateLimitKeyType;

/**
 * 标记某个Controller方法需要限流。具体限流维度/阈值/窗口由调用方在注解上声明，
 * 实际计数落在Redis（见RateLimitAspect），保证多实例部署下计数也是准的。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流维度：IP（未登录场景，如登录接口本身）或ACCOUNT（已登录场景，按currentId）
     */
    RateLimitKeyType keyType() default RateLimitKeyType.ACCOUNT;

    /**
     * 窗口内允许的最大请求数
     */
    int limit();

    /**
     * 窗口长度（秒）
     */
    int windowSeconds();

    /**
     * 这条限流规则的标识，用作Redis key前缀，也用于日志里区分是哪条规则触发的
     */
    String name();

    /**
     * 超限时返回给调用方的提示语
     */
    String message() default "操作过于频繁，请稍后再试";
}
