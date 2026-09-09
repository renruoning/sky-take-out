package com.sky.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.sky.datasource.DataSourceType;
import com.sky.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 读写分离：定义主/从两个真实数据源，再包一层{@link DynamicDataSource}对外暴露成唯一的
 * {@code @Primary DataSource} bean——MyBatis的SqlSessionFactory、Seata Saga状态机的
 * DbStateMachineConfig（见SagaStateMachineConfiguration）等所有原来直接注入DataSource的地方
 * 不用改一行代码，拿到的都是这个动态路由数据源，路由结果由DataSourceContextHolder当时的值决定。
 * <p>
 * 这个类顶掉了Spring Boot的DataSourceAutoConfiguration（后者是@ConditionalOnMissingBean(DataSource.class)，
 * 这里显式定义了DataSource bean，自动配置会自动让路，不用额外排除注解）。
 * <p>
 * 从库的url/username/password走独立的spring.datasource.slave前缀，缺省回退到主库同名配置——
 * demo环境主从复用同一套账号密码，真生产会给从库单独建一个只读账号
 */
@Configuration
public class DataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource masterDataSource(DataSourceProperties masterDataSourceProperties) {
        return masterDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.slave")
    public DataSourceProperties slaveDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource slaveDataSource(DataSourceProperties slaveDataSourceProperties) {
        return slaveDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSource masterDataSource, DataSource slaveDataSource) {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, masterDataSource);
        targetDataSources.put(DataSourceType.SLAVE, slaveDataSource);
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        return dynamicDataSource;
    }
}
