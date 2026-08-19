package com.sky.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
