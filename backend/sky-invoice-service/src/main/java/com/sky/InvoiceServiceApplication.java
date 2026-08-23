package com.sky;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Slf4j
public class InvoiceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceServiceApplication.class, args);
        log.info("sky-invoice-service started");
    }
}
