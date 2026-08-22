package com.sky.mq;

import java.time.Duration;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.sky.config.RabbitMQConfig;
import com.sky.websocket.WebSocketServer;

import lombok.extern.slf4j.Slf4j;

/**
 * 消费订单状态变更事件（来单提醒/客户催单），把WebSocket推送这个"通知"动作从下单/催单接口的同步链路里摘出来。
 * <p>
 * MQ只保证至少一次投递，同一条消息可能被重复消费（网络抖动、消费者重启都可能触发），
 * 用Redis记录已处理过的messageId做消费端幂等，避免商家端收到重复的来单提醒弹窗。
 */
@Component
@Slf4j
public class OrderEventNotifyListener {

    private static final String DEDUP_KEY_PREFIX = "mq:dedup:order_event_notify:";
    private static final Duration DEDUP_TTL = Duration.ofMinutes(10);

    private final WebSocketServer webSocketServer;
    private final StringRedisTemplate stringRedisTemplate;

    OrderEventNotifyListener(WebSocketServer webSocketServer, StringRedisTemplate stringRedisTemplate) {
        this.webSocketServer = webSocketServer;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_EVENT_NOTIFY_QUEUE,
            containerFactory = RabbitMQConfig.ORDER_EVENT_LISTENER_CONTAINER_FACTORY)
    public void handleOrderEvent(Map<String, Object> message) {
        String messageId = (String) message.get("messageId");
        if (messageId != null && !markProcessedIfAbsent(messageId)) {
            log.info("订单事件消息{}已经处理过，跳过（消费端幂等）", messageId);
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("type", message.get("type"));
        payload.put("orderId", message.get("orderId"));
        payload.put("content", message.get("content"));

        Object shopIdObj = message.get("shopId");
        Long shopId = shopIdObj == null ? null : Long.valueOf(shopIdObj.toString());
        webSocketServer.sendToShopClients(shopId, payload.toJSONString());
    }

    /**
     * Redis不可用时，把这条消息当成"没处理过"直接放行，不能因为幂等校验本身访问不到Redis就把通知漏掉——
     * 宁可极端情况下重复推送一次来单提醒，也不能完全不提醒
     */
    private boolean markProcessedIfAbsent(String messageId) {
        try {
            Boolean firstTime = stringRedisTemplate.opsForValue().setIfAbsent(DEDUP_KEY_PREFIX + messageId, "1", DEDUP_TTL);
            return firstTime != null && firstTime;
        } catch (Exception e) {
            log.error("消费端幂等校验访问Redis失败，本条消息按未处理过放行（fail-open）", e);
            return true;
        }
    }
}
