package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

// @EnableCaching是因为ReviewServiceImpl.getShopRatingSummary()用了@Cacheable——sky-server那边这个注解
// 挂在自定义的RedisConfiguration上，这个服务没有那么多自定义缓存配置，直接放主类上启用就够了，
// Spring Boot会用spring-boot-starter-data-redis自动配置好的默认RedisCacheManager
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCaching
@Slf4j
public class ReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
        log.info("sky-review-service started");
    }
}
