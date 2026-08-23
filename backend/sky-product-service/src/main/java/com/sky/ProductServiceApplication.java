package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// 不需要@EnableFeignClients——这个服务只被别人调用（sky-server通过Feign查它），自己不调用任何其它服务
@SpringBootApplication
@EnableDiscoveryClient
@Slf4j
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
        log.info("sky-product-service started");
    }
}
