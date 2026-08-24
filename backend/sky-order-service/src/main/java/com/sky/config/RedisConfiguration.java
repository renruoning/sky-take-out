package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.extern.slf4j.Slf4j;

/**
 * RedisTemplate<String, Object>给submit()的SETNX幂等锁、RateLimitAspect限流计数用。
 * 这个服务不用Spring Cache（@Cacheable）——shopListCache一度短暂接过Spring Cache（排查缓存穿透/
 * 雪崩/击穿防护时发现@EnableCaching漏加、注解形同虚设，先补上让它真正生效过），但紧接着又被换成了
 * com.sky.cache.LogicalExpireCache（逻辑过期+互斥锁异步刷新，专门防止这类首页级热key在物理过期瞬间
 * 被并发穿透，Spring Cache的@Cacheable做不到"过期时不阻塞、返回旧值"这一点），所以CacheManager/
 * @EnableCaching这些只为Spring Cache服务的配置已经不需要了，跟着一起撤掉，没有留哪怕一处不再生效的配置。
 * 跟sky-server/sky-product-service用的是同一份RedisTemplate定义。
 */
@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象");
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
