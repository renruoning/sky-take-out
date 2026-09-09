package com.sky.config;

import javax.sql.DataSource;

import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.config.DbStateMachineConfig;
import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import io.seata.saga.rm.StateMachineEngineHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 手写这两个bean，不用spring-cloud-starter-alibaba-seata自带的SeataSagaAutoConfiguration——
 * 那个类里dbStateMachineConfig()标了@ConditionalOnBean(DataSource.class)，但它在Spring Boot
 * 自动配置阶段被求值的时机早于这个服务自己的DataSourceAutoConfiguration把默认Hikari数据源
 * 注册进容器，导致条件恒为false、两个bean都不会被创建（实测复现：order-service启动时
 * "StateMachineEngine that could not be found"）。这里用普通@Configuration类在正常bean创建阶段
 * 注入DataSource，不受那个提前求值的时机问题影响。
 * <p>
 * @ConfigurationProperties("seata.saga.state-machine")保留在bean方法上，行为跟原来的
 * SeataSagaAutoConfiguration.dbStateMachineConfig()一致——application.yml里那段
 * seata.saga.state-machine.table-prefix/resources配置照样生效
 */
@Configuration
public class SagaStateMachineConfiguration {

    @Value("${spring.application.name}")
    private String applicationId;

    @Value("${seata.tx-service-group}")
    private String txServiceGroup;

    @Bean
    @ConfigurationProperties(prefix = "seata.saga.state-machine")
    public StateMachineConfig stateMachineConfig(DataSource dataSource) {
        DbStateMachineConfig config = new DbStateMachineConfig();
        config.setDataSource(dataSource);
        config.setApplicationId(applicationId);
        config.setTxServiceGroup(txServiceGroup);
        return config;
    }

    @Bean
    public StateMachineEngine stateMachineEngine(StateMachineConfig stateMachineConfig) {
        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(stateMachineConfig);
        // TC在异步重试/恢复场景下（比如report status: CommitRetrying）会通过这个静态Holder反查
        // 引擎实例——原来的SeataSagaAutoConfiguration.stateMachineEngine()会顺带注册这一步，
        // 这里手写替换掉那个类之后要自己补上，不然会在那类回调场景里报
        // NullPointerException: StateMachineEngineHolder.getStateMachineEngine() is null
        // （这也是实测复现过的，不是纸面推测）
        new StateMachineEngineHolder().setStateMachineEngine(engine);
        return engine;
    }
}
