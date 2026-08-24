package com.sky.mq;

import com.sky.config.RabbitMQConfig;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 消费订单支付超时的延迟消息
 */
@Component
@Slf4j
public class OrderTimeoutListener {

    // 跟OrderServiceImpl.userCancelById/cancel用的是同一把锁（同一个前缀+订单id），
    // 三条取消路径互斥。这个常量直接写死不引用OrderServiceImpl的（两个类不在同一个包里，
    // 没必要为了共享一个字符串前缀专门开放一个public常量，硬编码这一个字符串比额外的耦合更简单）
    private static final String CANCEL_LOCK_PREFIX = "lock:order:cancel:";
    private static final long CANCEL_LOCK_WAIT_SECONDS = 2;

    private final OrderMapper orderMapper;
    private final RedissonClient redissonClient;

    OrderTimeoutListener(OrderMapper orderMapper, RedissonClient redissonClient) {
        this.orderMapper = orderMapper;
        this.redissonClient = redissonClient;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        log.info("处理支付超时订单，订单id：{}", orderId);

        // 跟用户手动取消/管理端取消互斥，防止两边同时读到"还能取消"的旧状态各自写一次，
        // 取消原因/时间被后写的悄悄覆盖。这条路径抢不到锁不抛异常——这个队列没配DLQ/重试，
        // 异常会导致消息立刻重新入队变成死循环；抢不到就说明这一刻有别的路径正在处理这个订单，
        // 跳过这次、让消息自然确认消费掉就行，不需要重试（真被抢占了大概率是用户手动取消了，
        // 那这条超时消息本来也不该再生效）
        RLock lock = redissonClient.getLock(CANCEL_LOCK_PREFIX + orderId);
        boolean acquired;
        try {
            acquired = lock.tryLock(CANCEL_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取订单{}取消锁失败，Redisson不可达，本次放弃加锁直接执行（fail-open）", orderId, e);
            doCancel(orderId);
            return;
        }
        if (!acquired) {
            log.info("订单{}取消锁被占用（大概率正被用户/管理端取消），跳过本次超时自动取消", orderId);
            return;
        }
        try {
            doCancel(orderId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doCancel(Long orderId) {
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
