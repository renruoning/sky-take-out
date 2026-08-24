package com.sky.cache;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * 逻辑过期式缓存，用来防止极少数、极高并发的热key（店铺列表/菜品列表这类首页级聚合数据）在物理过期瞬间
 * 被成千上万个并发请求同时穿透到DB——跟{@code @Cacheable}是两回事：{@code @Cacheable}命中失效的瞬间，
 * 所有并发请求会一起排队等DB查完；这里换成谁的请求线程都不用等——过期只是"数据可能有点旧"，不是"缓存没了"。
 * <p>
 * 做法：Redis里的value本身自带一个逻辑过期时间戳，key不依赖Redis TTL过期（只给一个远大于逻辑TTL的物理TTL
 * 兜底，防止长期没人访问的key永久占内存）。读到期时直接把这份稍旧的数据立刻返回给调用方，同时抢一把很短的
 * Redis互斥锁去异步重新计算——抢到锁的线程后台刷新，抢不到的线程什么都不做，因为已经有人在做了。
 * <p>
 * 只适合"读多写少、能接受秒级陈旧、极少数key扛掉绝大多数请求"的场景（店铺信息、菜品展示都符合）；
 * 不适合库存扣减这类不能容忍脏数据的场景——那类场景更合适的做法是Redis原子自减，不是这个。
 * 跟sky-product-service用的是同一份定义。
 */
@Component
@Slf4j
public class LogicalExpireCache {

    private static final String REFRESH_LOCK_PREFIX = "lock:cache-refresh:";
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(10);
    // 只是兜底防止无人访问的key永久占内存，不驱动刷新——刷新完全靠上面的逻辑过期时间戳
    private static final Duration PHYSICAL_TTL_SAFETY_NET = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor refreshExecutor;

    public LogicalExpireCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        AtomicInteger threadCount = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "cache-refresh-" + threadCount.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.refreshExecutor = Executors.newFixedThreadPool(4, threadFactory);
    }

    /**
     * @param cacheKey  完整的Redis key
     * @param logicalTtl 逻辑过期时长，过了这个时长的数据依然会被立刻返回，只是会触发一次后台异步刷新
     * @param typeRef   反序列化目标类型
     * @param loader    缓存未命中/需要刷新时，实际去查DB的逻辑
     */
    public <T> T get(String cacheKey, Duration logicalTtl, TypeReference<T> typeRef, Supplier<T> loader) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.error("读取逻辑过期缓存[{}]失败，Redis不可达，本次请求跳过缓存直接查库（fail-open）", cacheKey, e);
            return loader.get();
        }

        if (json == null) {
            return loadColdSynchronously(cacheKey, logicalTtl, loader);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("逻辑过期缓存[{}]的内容解析失败，当作未命中处理，直接查库回填", cacheKey, e);
            return loadColdSynchronously(cacheKey, logicalTtl, loader);
        }

        long logicalExpireAt = root.get("logicalExpireAt").asLong();
        T data;
        try {
            data = objectMapper.convertValue(root.get("data"), objectMapper.getTypeFactory().constructType(typeRef));
        } catch (Exception e) {
            log.error("逻辑过期缓存[{}]的data字段反序列化失败，当作未命中处理，直接查库回填", cacheKey, e);
            return loadColdSynchronously(cacheKey, logicalTtl, loader);
        }

        if (logicalExpireAt > System.currentTimeMillis()) {
            return data;
        }

        // 逻辑已过期：不阻塞当前请求，先把旧数据立刻返回；只有抢到互斥锁的那一个线程去后台异步刷新，
        // 抢不到锁说明已经有别的请求在刷新了，直接跳过，避免同一时刻几千个并发请求一起触发几千次异步刷新任务
        String lockKey = REFRESH_LOCK_PREFIX + cacheKey;
        boolean lockAcquired;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", REFRESH_LOCK_TTL);
            lockAcquired = Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("抢占逻辑过期缓存[{}]的刷新锁失败，本次放弃后台刷新，先返回旧数据", cacheKey, e);
            lockAcquired = false;
        }
        if (lockAcquired) {
            refreshExecutor.execute(() -> refreshAsync(cacheKey, logicalTtl, loader));
        }
        return data;
    }

    /**
     * 清除单个key，供写路径调用（新增/修改后让下一次读自然重新回填，走loadColdSynchronously那条路）
     */
    public void evict(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.error("清除逻辑过期缓存[{}]失败，忽略（下次刷新前这个key会继续返回旧数据）", cacheKey, e);
        }
    }

    /**
     * 按前缀批量清除，供"改动影响范围不确定，索性清空整个分类"的写路径调用
     */
    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("按前缀[{}]批量清除逻辑过期缓存失败，忽略", prefix, e);
        }
    }

    private <T> T loadColdSynchronously(String cacheKey, Duration logicalTtl, Supplier<T> loader) {
        // key完全不存在（服务刚启动/被evict过）比"逻辑过期但仍存在"少见得多，这里不再额外加锁排队等待——
        // 直接查库返回，容忍极少数场景下有几个并发请求一起查一次库，换取实现简单
        T data = loader.get();
        writeThrough(cacheKey, logicalTtl, data);
        return data;
    }

    private <T> void refreshAsync(String cacheKey, Duration logicalTtl, Supplier<T> loader) {
        try {
            T fresh = loader.get();
            writeThrough(cacheKey, logicalTtl, fresh);
        } catch (Exception e) {
            log.error("逻辑过期缓存[{}]后台异步刷新失败，保留旧数据，等下一个请求触发的刷新窗口再试", cacheKey, e);
        }
    }

    private <T> void writeThrough(String cacheKey, Duration logicalTtl, T data) {
        try {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("logicalExpireAt", System.currentTimeMillis() + logicalTtl.toMillis());
            wrapper.set("data", objectMapper.valueToTree(data));
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(wrapper), PHYSICAL_TTL_SAFETY_NET);
        } catch (Exception e) {
            log.error("写入逻辑过期缓存[{}]失败，忽略（下次请求会重新触发这条路径）", cacheKey, e);
        }
    }
}
