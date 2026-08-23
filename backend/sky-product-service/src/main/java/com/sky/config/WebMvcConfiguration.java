package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跟sky-review-service一样，这个服务同时有用户端（浏览菜单）和管理端（菜品/套餐/分类管理）接口，
 * 两种JWT都要接
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
                .addPathPatterns("/user/dish/**", "/user/setmeal/**", "/user/category/**");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/dish/**", "/admin/setmeal/**", "/admin/category/**");
    }
}
