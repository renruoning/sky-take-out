package com.sky.aspect;

import java.lang.reflect.Method;
import java.time.Duration;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sky.annotation.RateLimit;
import com.sky.context.BaseContext;
import com.sky.enumeration.RateLimitKeyType;
import com.sky.exception.RateLimitException;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用限流切面：跟sky-server里的同名类逻辑一致（复制自sky-server/aspect/RateLimitAspect，
 * 详见那边类注释里记录的两个真实踩过的坑：@Cacheable命中会让排在内侧的限流切面短路失效、
 * 加@Order后@annotation(rateLimit)参数绑定写法会抛异常）。这个服务目前没有@Cacheable，
 * 不受第一个问题影响，这里保留同样的写法只是为了两份代码保持一致、便于对照维护。
 * <p>
 * 这里没有把这个类挪到sky-common里共享——sky-common现在没有spring-web/spring-data-redis依赖，
 * 为了一个切面扩大共享库的依赖面性价比不高，两个服务各自维护一份在这个规模下是合理的取舍。
 * <p>
 * 用StringRedisTemplate而不是sky-server那边的RedisTemplate&lt;String,Object&gt;——限流计数器只存字符串，
 * 不需要sky-server那个自定义RedisConfiguration提供的通用序列化template，StringRedisTemplate是
 * Spring Boot自动配置好的现成bean，不用为这一个切面单独再写一个配置类。
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    RateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(com.sky.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        String key = buildKey(rateLimit);
        if (key != null) {
            try {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redisTemplate.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
                }
                if (count != null && count > rateLimit.limit()) {
                    log.warn("触发限流[{}]：key={}，{}秒内已请求{}次（上限{}）",
                            rateLimit.name(), key, rateLimit.windowSeconds(), count, rateLimit.limit());
                    throw new RateLimitException(rateLimit.message());
                }
            } catch (RateLimitException e) {
                throw e;
            } catch (Exception e) {
                log.error("限流组件[{}]访问Redis失败，本次请求放行（fail-open）", rateLimit.name(), e);
            }
        }
        return joinPoint.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        String identity;
        if (rateLimit.keyType() == RateLimitKeyType.ACCOUNT) {
            Long currentId = BaseContext.getCurrentId();
            if (currentId == null) {
                return null;
            }
            identity = String.valueOf(currentId);
        } else {
            identity = resolveClientIp();
            if (identity == null) {
                return null;
            }
        }
        return "rate_limit:" + rateLimit.name() + ":" + identity;
    }

    private String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
