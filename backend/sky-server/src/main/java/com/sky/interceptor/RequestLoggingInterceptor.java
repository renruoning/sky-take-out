package com.sky.interceptor;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.sky.context.BaseContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局访问日志：每个请求打一行结构化的方法/路径/状态码/耗时/操作人日志，并给每个请求分配一个traceId写进MDC，
 * 方便把同一个请求在controller/service/mapper各层打的散落日志行关联起来看。
 * <p>
 * 现在排查问题基本靠肉眼翻log文件，光有分散的debug日志、没有"这次请求到底发生了什么"的汇总视角，
 * 这一条日志本身不是ELK/SkyWalking那种集中采集或链路追踪，但已有的日志格式统一带上traceId后，
 * 后续接ELK/Loki时可以直接按这个字段过滤，不需要再回头改代码。
 */
@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String START_TIME_ATTR = "__requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            Object startTimeObj = request.getAttribute(START_TIME_ATTR);
            long duration = startTimeObj != null ? System.currentTimeMillis() - (long) startTimeObj : -1;
            // 这个拦截器必须注册在jwt拦截器之后，afterCompletion才会先于jwt的afterCompletion执行，
            // 此时BaseContext还没被清空，能拿到是谁发起的这次请求（管理端empId或用户端userId）
            Long currentId = BaseContext.getCurrentId();
            log.info("请求完成：{} {} status={} duration={}ms operator={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), duration, currentId);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
