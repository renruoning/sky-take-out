package com.sky.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.sky.datasource.DataSourceContextHolder;
import com.sky.datasource.DataSourceType;

/**
 * 拦截标了{@link com.sky.annotation.Slave}的方法，把当前线程的数据源路由标记设成SLAVE，
 * 方法执行期间MyBatis拿到的物理连接来自从库；方法返回后一律清掉标记，不能让标记泄漏到
 * 线程池里同一线程处理的下一个请求上（Tomcat线程复用，不clear会导致下一个完全无关的请求
 * 也被错误地路由到从库）。
 * <p>
 * 跟RateLimitAspect一样显式声明@Order(HIGHEST_PRECEDENCE)排最外层——数据源要在方法体内
 * 第一次触发MyBatis获取连接之前就确定好，排太靠内层、被其它切面（比如涉及DB操作的切面）
 * 包在外面的话，标记生效前可能已经有连接被拿走
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlaveRoutingAspect {

    @Around("@annotation(com.sky.annotation.Slave)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        try {
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
