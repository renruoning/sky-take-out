package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 这个服务同时有用户端（下单/查订单/店铺列表）和管理端（订单处理/店铺设置）接口，两种JWT都要接。
 * WebSocket端点（/ws/{sid}）不走这个拦截器链，是原生JSR-356端点自己解析token鉴权，见WebSocketServer。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;
    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    WebMvcConfiguration(JwtTokenUserInterceptor jwtTokenUserInterceptor, JwtTokenAdminInterceptor jwtTokenAdminInterceptor) {
        this.jwtTokenUserInterceptor = jwtTokenUserInterceptor;
        this.jwtTokenAdminInterceptor = jwtTokenAdminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/order/**", "/user/shop/**");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/order/**", "/admin/shop/**");
    }
}
