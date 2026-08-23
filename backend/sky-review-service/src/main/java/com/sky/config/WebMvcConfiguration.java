package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 这个服务既有用户端接口（/user/review/**，提交评价/查看评价列表）也有管理端接口
 * （/admin/review/**，商家回复评价），两种JWT都要接——这是跟sky-ai-service/sky-invoice-service
 * 不一样的地方（那两个只有用户端接口，只接了JwtTokenUserInterceptor一个）
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
        registry.addInterceptor(jwtTokenUserInterceptor).addPathPatterns("/user/review/**");
        registry.addInterceptor(jwtTokenAdminInterceptor).addPathPatterns("/admin/review/**");
    }
}
