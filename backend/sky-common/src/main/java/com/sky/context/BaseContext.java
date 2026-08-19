package com.sky.context;

public class BaseContext {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    // 当前请求的店铺id：员工端来自JWT（为空表示平台超管），用户端由前端显式传入后手动设置；null表示无店铺上下文
    public static ThreadLocal<Long> shopIdThreadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

    public static void setCurrentShopId(Long shopId) {
        shopIdThreadLocal.set(shopId);
    }

    public static Long getCurrentShopId() {
        return shopIdThreadLocal.get();
    }

    public static void removeCurrentShopId() {
        shopIdThreadLocal.remove();
    }

}
