package com.sky.handler;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis缓存（@Cacheable/@CacheEvict等）访问失败时的兜底策略：记日志、不抛异常。
 * <p>
 * 默认行为是异常直接往外抛，等价于"缓存组件挂了，被它缓存的业务接口也跟着挂"——
 * 缓存本该是锦上添花，不该成为主流程的单点故障。get失败按缓存未命中处理（回源查DB），
 * put/evict失败只是意味着这次没更新上缓存，下次请求会自然重新回源，不影响数据正确性。
 */
@Component
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.error("读取缓存[{}]失败，key={}，本次请求按缓存未命中处理（回源查询）", cache.getName(), key, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.error("写入缓存[{}]失败，key={}，忽略（下次请求会重新回源）", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.error("清除缓存[{}]失败，key={}，忽略", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.error("清空缓存[{}]失败，忽略", cache.getName(), exception);
    }
}
