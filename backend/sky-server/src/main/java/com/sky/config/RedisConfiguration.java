package com.sky.config;

import java.time.Duration;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.sky.handler.LoggingCacheErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfiguration implements CachingConfigurer {

    private final LoggingCacheErrorHandler loggingCacheErrorHandler;

    RedisConfiguration(LoggingCacheErrorHandler loggingCacheErrorHandler) {
        this.loggingCacheErrorHandler = loggingCacheErrorHandler;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return loggingCacheErrorHandler;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建redis模板对象");
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        //设置redis的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    /**
     * Spring Cache 使用的CacheManager，基于Redis存储，默认1小时过期（写入时额外加±10%随机抖动，见JitteredRedisCacheWriter）
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1));
        RedisCacheWriter jitteredWriter = new JitteredRedisCacheWriter(
                RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory));
        return RedisCacheManager.builder(jitteredWriter)
                .cacheDefaults(config)
                .build();
    }
}
