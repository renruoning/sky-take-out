package com.sky.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.json.JacksonObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sky-server原来只在WebMvcConfiguration.extendMessageConverters()里手动往MVC的转换器列表插入了一个
 * JacksonObjectMapper实例，这只覆盖了Controller自己的请求/响应——Feign作为客户端调用其它服务时，
 * 用的是Spring Boot自动配置的默认ObjectMapper（不是这个手动插入的转换器），日期格式对不上
 * product-service返回的"yyyy-MM-dd HH:mm"格式，直接在解析Feign响应时抛DateTimeParseException
 * （实测复现：购物车加菜品时调ProductClient.getDish()崩溃）。声明成正式的Spring Bean之后，
 * Feign的默认解码器和MVC的消息转换器就用的是同一个ObjectMapper了，两条路径的日期格式统一。
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new JacksonObjectMapper();
    }
}
