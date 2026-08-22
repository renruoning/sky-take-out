package com.sky.enumeration;

/**
 * 限流维度：按客户端IP，或按已登录账号（管理端员工id/用户端userId，统一取自BaseContext）
 */
public enum RateLimitKeyType {
    IP,
    ACCOUNT
}
