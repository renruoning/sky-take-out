package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

// @EnableCaching是因为ReviewServiceImpl.getShopRatingSummary()用了@Cacheable。CacheManager本身
// 现在由config/RedisConfiguration提供（跟sky-server/sky-product-service/sky-order-service对齐，
// 有TTL抖动防雪崩+Redis故障降级），不再是Spring Boot的默认CacheManager（默认没有TTL、故障直接抛异常）
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
