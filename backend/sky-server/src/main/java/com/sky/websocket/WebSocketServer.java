package com.sky.websocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 处理和WebSocket相关的业务：与商家端管理页面保持长连接，供后端主动推送来单提醒、客户催单消息
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    // 存放会话对象，key为sid
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    // 存放每个会话所属的店铺id，key为sid；连接时未携带shopId查询参数（老前端）的会话不会出现在这里，视为“未限定店铺”
    private static final Map<String, Long> SID_SHOP_MAP = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{} 建立连接", sid);
        SESSION_MAP.put(sid, session);

        // 尝试从连接url的查询参数里读取shopId（新前端会带上，老前端不带，兼容处理见sendToShopClients）
        List<String> shopIdParams = session.getRequestParameterMap().get("shopId");
        if (shopIdParams != null && !shopIdParams.isEmpty()) {
            try {
                SID_SHOP_MAP.put(sid, Long.valueOf(shopIdParams.get(0)));
            } catch (NumberFormatException e) {
                log.warn("客户端：{} 传入的shopId参数无法解析：{}", sid, shopIdParams.get(0));
            }
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
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开：{}", sid);
        SESSION_MAP.remove(sid);
        SID_SHOP_MAP.remove(sid);
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
                log.error("向客户端：{} 推送消息失败", sid, e);
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
                log.error("向客户端：{} 推送消息失败", sid, e);
            }
        });
    }
}
