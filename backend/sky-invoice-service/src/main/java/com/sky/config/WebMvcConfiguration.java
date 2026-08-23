package com.sky.config;

import com.sky.interceptor.JwtTokenUserInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 只注册一个用户端jwt拦截器，不像sky-server那样继承WebMvcConfigurationSupport手动接管资源映射/消息转换器——
 * 这个服务没有knife4j/swagger，用Spring Boot的MVC自动配置就够了
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;

    WebMvcConfiguration(JwtTokenUserInterceptor jwtTokenUserInterceptor) {
        this.jwtTokenUserInterceptor = jwtTokenUserInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenUserInterceptor).addPathPatterns("/user/invoice/**");
    }
}
