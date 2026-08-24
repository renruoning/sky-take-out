package com.sky.config;

import java.time.Duration;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.sky.handler.LoggingCacheErrorHandler;

/**
 * 之前这个服务没有这个类，@EnableCaching单独放在ReviewServiceApplication上、CacheManager完全交给
 * Spring Boot的默认自动配置——这意味着shopRatingSummaryCache的key永不过期（默认没有TTL）、
 * Redis读写失败时默认的CacheErrorHandler会直接把异常往外抛（get/put失败=接口跟着500），
 * 跟sky-server/sky-product-service/sky-order-service这三个服务已经有的"缓存故障降级、TTL加抖动"
 * 防护完全不对等。排查缓存穿透/雪崩/击穿防护时补上，跟另外三个服务用同一套定义对齐。
 */
@Configuration
public class RedisConfiguration implements CachingConfigurer {

    private final LoggingCacheErrorHandler loggingCacheErrorHandler;

    RedisConfiguration(LoggingCacheErrorHandler loggingCacheErrorHandler) {
        this.loggingCacheErrorHandler = loggingCacheErrorHandler;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return loggingCacheErrorHandler;
    }

    /**
     * Spring Cache 使用的CacheManager，基于Redis存储，默认1小时过期（写入时额外加±10%随机抖动，见JitteredRedisCacheWriter）。
     * shopRatingSummaryCache的正确性主要靠submit()里的@CacheEvict手动失效保证，这个TTL只是兜底安全网，不是主要的新鲜度机制。
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
