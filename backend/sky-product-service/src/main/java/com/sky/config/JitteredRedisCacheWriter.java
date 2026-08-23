package com.sky.config;

import java.time.Duration;
import java.util.Random;

import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

/**
 * 给所有走Spring Cache（@Cacheable等）落地的key的TTL加一点随机抖动，防止缓存雪崩：
 * 如果大量key是同一时刻批量写入的（比如服务刚启动、缓存刚被清空后一波请求同时回源），
 * 用固定TTL会导致它们在未来同一时刻集体过期，瞬间又把这波请求全部打回数据库。
 * 加了±10%的随机抖动后，各个key的实际过期时间会自然错开。
 */
public class JitteredRedisCacheWriter implements RedisCacheWriter {

    private static final double JITTER_RATIO = 0.1;

    private final RedisCacheWriter delegate;
    private final Random random = new Random();

    public JitteredRedisCacheWriter(RedisCacheWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        delegate.put(name, key, value, jitter(ttl));
    }

    @Override
    public byte[] get(String name, byte[] key) {
        return delegate.get(name, key);
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.putIfAbsent(name, key, value, jitter(ttl));
    }

    @Override
    public void remove(String name, byte[] key) {
        delegate.remove(name, key);
    }

    @Override
    public void clean(String name, byte[] pattern) {
        delegate.clean(name, pattern);
    }

    @Override
    public void clearStatistics(String name) {
        delegate.clearStatistics(name);
    }

    @Override
    public CacheStatistics getCacheStatistics(String cacheName) {
        return delegate.getCacheStatistics(cacheName);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return new JitteredRedisCacheWriter(delegate.withStatisticsCollector(cacheStatisticsCollector));
    }

    private Duration jitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ttl;
        }
        double factor = 1 + (random.nextDouble() * 2 - 1) * JITTER_RATIO;
        long jitteredMillis = Math.max((long) (ttl.toMillis() * factor), 1000L);
        return Duration.ofMillis(jitteredMillis);
    }
}
