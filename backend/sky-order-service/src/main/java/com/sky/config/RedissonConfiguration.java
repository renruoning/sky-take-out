package com.sky.config;

import java.util.Arrays;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 取消订单场景的分布式锁（RLock）用的RedissonClient——复用跟这个服务其它Redis连接同一套Sentinel拓扑
 * （sky.redis.sentinel-master/sentinel-nodes，已经写进spring.redis.sentinel.*），不新起一套Redis。
 * 没用redisson-spring-boot-starter的自动配置，手写这个bean的原因跟RedisConfiguration一样：
 * 这个服务里跟Redis相关的配置一直都是显式@Bean，不依赖某个starter猜配置猜对了。
 */
@Configuration
public class RedissonConfiguration {

    @Value("${spring.redis.sentinel.master}")
    private String sentinelMaster;

    @Value("${spring.redis.sentinel.nodes}")
    private String sentinelNodes;

    @Value("${spring.redis.database:0}")
    private int database;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String[] addresses = Arrays.stream(sentinelNodes.split(","))
                .map(String::trim)
                .map(addr -> "redis://" + addr)
                .toArray(String[]::new);

        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setMasterName(sentinelMaster)
                .addSentinelAddress(addresses)
                .setDatabase(database);
        if (password != null && !password.isEmpty()) {
            sentinelServersConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
