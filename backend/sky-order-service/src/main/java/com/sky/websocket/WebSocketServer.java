package com.sky.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sky.constant.JwtClaimsConstant;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;

import lombok.extern.slf4j.Slf4j;

/**
 * 处理和WebSocket相关的业务：与商家端管理页面保持长连接，供后端主动推送来单提醒、客户催单消息
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    // 连接由javax.websocket容器直接实例化，不会走Spring构造器注入；
    // 这里借助Spring托管的单例bean在启动时把admin端jwt配置写进静态字段，供每个连接实例的onOpen使用
    private static JwtProperties jwtProperties;

    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        WebSocketServer.jwtProperties = jwtProperties;
    }

    // 存放会话对象，key为sid
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    // 存放每个会话所属的店铺id，key为sid；平台超管token不带shopId claim，不会出现在这里，视为“可见所有店铺”
    private static final Map<String, Long> SID_SHOP_MAP = new ConcurrentHashMap<>();

    // 每个会话最近一次收到心跳（或建连）的时间戳，key为sid；用于清理网络异常断开、没有触发onClose的僵尸连接
    private static final Map<String, Long> LAST_ACTIVE_MAP = new ConcurrentHashMap<>();

    // 前端每25秒发一次心跳（见admin.html），允许错过2次再判定失联，避免单次网络抖动就误杀正常连接
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 75 * 1000L;

    /**
     * 连接建立成功调用的方法。
     * 鉴权在握手阶段完成：token必须能用admin端密钥解析成功，店铺归属只认token里的shopId claim，
     * 不再信任客户端在连接URL上自报的shopId——否则任何人都能填别的店铺id，偷看该店铺的来单/催单通知。
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        String token = firstParam(session, "token");
        if (jwtProperties == null || token == null) {
            log.warn("客户端：{} 未携带有效token，拒绝建立WebSocket连接", sid);
            closeQuietly(session, "missing token");
            return;
        }

        Long shopId;
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Object shopIdClaim = claims.get(JwtClaimsConstant.SHOP_ID);
            shopId = shopIdClaim != null ? Long.valueOf(shopIdClaim.toString()) : null;
        } catch (Exception e) {
            log.warn("客户端：{} token校验失败，拒绝建立WebSocket连接", sid, e);
            closeQuietly(session, "invalid token");
            return;
        }

        log.info("客户端：{} 建立连接，shopId={}", sid, shopId);
        SESSION_MAP.put(sid, session);
        LAST_ACTIVE_MAP.put(sid, System.currentTimeMillis());
        if (shopId != null) {
            SID_SHOP_MAP.put(sid, shopId);
        }
    }

    private String firstParam(Session session, String name) {
        List<String> values = session.getRequestParameterMap().get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void closeQuietly(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (Exception e) {
            log.warn("关闭未鉴权的WebSocket连接失败", e);
        }
    }

    /**
     * 收到客户端消息后调用的方法（用于心跳保活）
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端：{} 的信息：{}", sid, message);
        LAST_ACTIVE_MAP.put(sid, System.currentTimeMillis());
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开：{}", sid);
        removeSession(sid);
    }

    private void removeSession(String sid) {
        SESSION_MAP.remove(sid);
        SID_SHOP_MAP.remove(sid);
        LAST_ACTIVE_MAP.remove(sid);
    }

    /**
     * 定时清理心跳超时的僵尸连接：网络异常断开（拔网线、进程被杀、路由器重启）不会触发onClose，
     * SESSION_MAP里的条目会一直留着，长期运行造成内存泄漏，且推送时还会对着这些死连接做无意义的发送尝试。
     * 每30秒扫一次，把超过HEARTBEAT_TIMEOUT_MILLIS没收到心跳的连接主动关闭并清理。
     */
    @Scheduled(fixedRate = 30 * 1000L)
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        for (Entry<String, Long> entry : new HashMap<>(LAST_ACTIVE_MAP).entrySet()) {
            String sid = entry.getKey();
            long lastActive = entry.getValue();
            if (now - lastActive <= HEARTBEAT_TIMEOUT_MILLIS) {
                continue;
            }
            log.warn("客户端：{} 心跳超时（超过{}ms未活跃），主动断开并清理", sid, HEARTBEAT_TIMEOUT_MILLIS);
            Session session = SESSION_MAP.get(sid);
            removeSession(sid);
            if (session != null) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "heartbeat timeout"));
                } catch (Exception e) {
                    log.warn("关闭心跳超时的WebSocket连接失败", e);
                }
            }
        }
    }

    /**
     * 群发消息（不区分店铺，保留供无店铺上下文的场景使用）
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        Map<String, Session> sessionMap = new HashMap<>(SESSION_MAP);
        sessionMap.forEach((sid, session) -> {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("向客户端：{} 推送消息失败，判定为死连接，清理", sid, e);
                removeSession(sid);
            }
        });
    }

    /**
     * 只推送给指定店铺的客户端。
     * 未携带shopId连接的客户端（老前端，尚未适配店铺参数）仍会收到所有店铺的消息，保持向后兼容；
     * 已声明shopId的客户端只会收到与自己店铺匹配的消息，实现按店铺路由。
     *
     * @param shopId  消息所属店铺id
     * @param message
     */
    public void sendToShopClients(Long shopId, String message) {
        if (shopId == null) {
            // 理论上不会出现：来单/催单消息一定挂在某个具体订单上，订单必属于某个店铺
            sendToAllClient(message);
            return;
        }
        Map<String, Session> sessionMap = new HashMap<>(SESSION_MAP);
        sessionMap.forEach((sid, session) -> {
            Long sessionShopId = SID_SHOP_MAP.get(sid);
            if (sessionShopId != null && !sessionShopId.equals(shopId)) {
                // 已声明属于其他店铺的客户端，跳过
                return;
            }
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("向客户端：{} 推送消息失败，判定为死连接，清理", sid, e);
                removeSession(sid);
            }
        });
    }
}
