package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// @EnableFeignClients给RatingClient用（user/ShopController.list()调sky-review-service要评分汇总）；
// @EnableScheduling给OrderTask（凌晨强制完成超时配送订单）用；@EnableTransactionManagement给
// OrderServiceImpl.submit()的@Transactional用——原样从sky-server的SkyApplication搬过来
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
@EnableScheduling
@Slf4j
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
        log.info("sky-order-service started");
    }
}
