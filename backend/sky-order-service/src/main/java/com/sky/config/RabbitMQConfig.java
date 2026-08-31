package com.sky.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;

/**
 * 订单超时延迟队列配置
 * 下单后消息进入延迟队列，停留 ORDER_TIMEOUT_TTL 后因无人消费而过期，
 * 依据死信交换机/路由键转发到真正消费的超时处理队列，从而实现"N分钟后处理支付超时订单"
 */
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "sky.order.exchange";

    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "sky.order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_QUEUE = "sky.order.timeout.queue";

    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout.dead";

    /**
     * 支付超时时间：30分钟
     */
    public static final long ORDER_TIMEOUT_TTL = 30 * 60 * 1000L;

    /**
     * 订单状态变更事件广播（下单成功、催单）：把"改状态"和"发通知"解耦，
     * 状态变更本身走原来的同步事务，通知（WebSocket推送）改成发消息到这个队列异步消费，
     * 不会因为推送慢/推送失败拖慢下单/催单接口本身的响应
     */
    public static final String ORDER_EVENT_NOTIFY_QUEUE = "sky.order.event.notify.queue";
    public static final String ORDER_EVENT_NOTIFY_ROUTING_KEY = "order.event.notify";
    public static final String ORDER_EVENT_DLX = "sky.order.event.dlx";
    public static final String ORDER_EVENT_NOTIFY_DLQ = "sky.order.event.notify.dlq";
    public static final String ORDER_EVENT_NOTIFY_DEAD_ROUTING_KEY = "order.event.notify.dead";

    public static final String ORDER_EVENT_LISTENER_CONTAINER_FACTORY = "orderEventListenerContainerFactory";

    /**
     * 支付超时队列的重试死信配置，对齐订单事件通知那条链路：消费失败（比如doCancel里
     * orderMapper.update瞬时失败）重试耗尽后不再无限requeue，转发到独立的DLQ人工介入。
     * 跟"抢不到取消锁"这种情况分开看——那种是业务代码里主动return，不抛异常，不会触发这里的重试，
     * 只有真正的异常（DB连接问题等）才会走到这套重试+死信流程
     */
    public static final String ORDER_TIMEOUT_DLX = "sky.order.timeout.dlx";
    public static final String ORDER_TIMEOUT_RETRY_DLQ = "sky.order.timeout.retry.dlq";
    public static final String ORDER_TIMEOUT_RETRY_DEAD_ROUTING_KEY = "order.timeout.retry.dead";
    public static final String ORDER_TIMEOUT_LISTENER_CONTAINER_FACTORY = "orderTimeoutListenerContainerFactory";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderTimeoutDelayQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_DELAY_QUEUE)
                .withArgument("x-message-ttl", ORDER_TIMEOUT_TTL)
                .withArgument("x-dead-letter-exchange", ORDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderTimeoutDelayBinding() {
        return BindingBuilder.bind(orderTimeoutDelayQueue())
                .to(orderExchange())
                .with(ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        // x-dead-letter-exchange这里管的是"消费失败"（重试3次耗尽后reject-and-dont-requeue）转发去哪，
        // 跟队列怎么被投递进来（延迟队列TTL到期后的死信路由）是两件独立的事，互不冲突
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_TIMEOUT_RETRY_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderTimeoutDlx() {
        return new DirectExchange(ORDER_TIMEOUT_DLX);
    }

    @Bean
    public Queue orderTimeoutRetryDlq() {
        return QueueBuilder.durable(ORDER_TIMEOUT_RETRY_DLQ).build();
    }

    @Bean
    public Binding orderTimeoutRetryDlqBinding() {
        return BindingBuilder.bind(orderTimeoutRetryDlq())
                .to(orderTimeoutDlx())
                .with(ORDER_TIMEOUT_RETRY_DEAD_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderEventDlx() {
        return new DirectExchange(ORDER_EVENT_DLX);
    }

    @Bean
    public Queue orderEventNotifyDlq() {
        return QueueBuilder.durable(ORDER_EVENT_NOTIFY_DLQ).build();
    }

    @Bean
    public Binding orderEventNotifyDlqBinding() {
        return BindingBuilder.bind(orderEventNotifyDlq())
                .to(orderEventDlx())
                .with(ORDER_EVENT_NOTIFY_DEAD_ROUTING_KEY);
    }

    @Bean
    public Queue orderEventNotifyQueue() {
        // 消费失败重试耗尽后，走下面配的死信交换机转发到DLQ，而不是无限requeue形成死循环或者被broker默默丢弃
        return QueueBuilder.durable(ORDER_EVENT_NOTIFY_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_EVENT_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_EVENT_NOTIFY_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderEventNotifyBinding() {
        return BindingBuilder.bind(orderEventNotifyQueue())
                .to(orderExchange())
                .with(ORDER_EVENT_NOTIFY_ROUTING_KEY);
    }

    /**
     * 只给新加的订单事件通知队列用的容器工厂：最多重试3次（间隔1s起步、指数退避到10s封顶），
     * 重试耗尽后reject-and-dont-requeue，配合队列上的死信配置转发到DLQ。
     * 订单超时那个监听器同款配置见下面的orderTimeoutListenerContainerFactory。
     */
    @Bean(ORDER_EVENT_LISTENER_CONTAINER_FACTORY)
    public SimpleRabbitListenerContainerFactory orderEventListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        // 重试3次全部失败后，拦截器默认把最后一次异常原样往外抛，
        // 配合上面defaultRequeueRejected(false)，容器会reject-and-dont-requeue，触发队列上配的死信路由转发到DLQ
        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .build();
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }

    /**
     * 支付超时队列用的容器工厂，跟订单事件通知那个一样配重试：最多3次，1s起步指数退避到10s封顶，
     * 耗尽后reject-and-dont-requeue，配合上面orderTimeoutQueue()的死信配置转发到retry DLQ。
     * "抢不到取消锁"这种情况是OrderTimeoutListener业务代码里主动return、不抛异常，不会被这层重试拦截器
     * 当成失败处理——只有orderMapper.update/productClient.restoreStock这类真实抛出的异常才会触发重试
     */
    @Bean(ORDER_TIMEOUT_LISTENER_CONTAINER_FACTORY)
    public SimpleRabbitListenerContainerFactory orderTimeoutListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .build();
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
