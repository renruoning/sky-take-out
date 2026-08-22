package com.sky.aspect;

import java.time.Duration;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sky.annotation.RateLimit;
import com.sky.context.BaseContext;
import com.sky.enumeration.RateLimitKeyType;
import com.sky.exception.RateLimitException;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用限流切面：拦截所有标了{@link RateLimit}注解的Controller方法，
 * 按注解声明的维度（IP/账号）在Redis里用固定窗口计数（INCR+EXPIRE），超限直接拒绝。
 * <p>
 * 限流是可用性的辅助手段，不能反过来因为Redis本身不可用而拖垮主业务——
 * 所以这里对Redis访问失败做了fail-open处理：记日志、放行，而不是让请求跟着失败。
 * <p>
 * 必须显式声明{@code @Order}排在最外层：Spring默认对没有指定Order的自定义切面和内置的
 * 缓存切面（{@code CacheInterceptor}）一视同仁按声明顺序排序，实测发现如果限流切面排在缓存切面内侧，
 * 一旦命中`@Cacheable`缓存直接短路返回，根本不会走到这里——等于"查同一个分类第二次开始限流形同虚设"，
 * 这是真实复现过的bug，不是理论风险。排最外层保证不管缓存命中与否，每次调用都会先经过计数。
 * <p>
 * 这里没有用{@code @Around("@annotation(rateLimit)")}让Spring把注解实例绑定成方法参数——
 * 实测发现一旦切面之间存在显式排序（比如这里加的{@code @Order}），这种参数绑定的写法会在
 * 多切面链路里抛`Required to bind 2 arguments, but only bound 1 (JoinPointMatch was NOT bound in invocation)`，
 * 是Spring AOP在AspectJ切点参数绑定和多层代理链交互时的已知问题。改成用纯类型pointcut
 * + 反射拿方法上的注解，绕开参数绑定，就不受这个限制影响。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    RateLimitAspect(RedisTemplate<String, Object> redisTemplate) {
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
            // 拿不到当前登录身份（理论上不会发生在需要鉴权的接口上）就不限流，不能因为限流逻辑本身出问题挡住正常请求
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
        // 项目部署在nginx反代后面，真实客户端IP在X-Forwarded-For里
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
