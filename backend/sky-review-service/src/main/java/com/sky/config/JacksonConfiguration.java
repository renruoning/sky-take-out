package com.sky.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.json.JacksonObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sky-server的所有JSON响应都用sky-common里这个自定义ObjectMapper（LocalDateTime格式是"yyyy-MM-dd HH:mm"，
 * 不是Jackson默认的ISO-8601）。这个服务如果继续用Spring Boot自动配置的默认ObjectMapper，会有两个问题：
 * ①Feign解码sky-server返回的日期字符串时直接抛DateTimeParseException（已经实测复现过）；
 * ②自己接口返回的日期格式会跟系统其它接口不一致，前端按老格式做字符串处理的地方可能出问题。
 * 声明这个bean之后Spring Boot的Jackson自动配置会让位（单一候选），Feign的编解码器和这个服务自己的
 * Controller响应会统一用同一个ObjectMapper。
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new JacksonObjectMapper();
    }
}
