package com.sky.mq;

import com.sky.config.RabbitMQConfig;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费订单支付超时的延迟消息
 */
@Component
@Slf4j
public class OrderTimeoutListener {

    private final OrderMapper orderMapper;

    OrderTimeoutListener(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        log.info("处理支付超时订单，订单id：{}", orderId);

        Orders orders = orderMapper.getById(orderId);
        // 订单可能已支付或已被用户取消，只在仍处于待支付状态时才自动取消，保证消息重复投递时的幂等性
        if (orders != null && Orders.PENDING_PAYMENT.equals(orders.getStatus())) {
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason("支付超时，自动取消");
            orders.setCancelTime(LocalDateTime.now());
            orderMapper.update(orders);
        }
    }
}
