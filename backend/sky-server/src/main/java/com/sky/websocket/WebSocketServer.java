package com.sky.websocket;

import java.util.HashMap;
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

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{} 建立连接", sid);
        SESSION_MAP.put(sid, session);
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
    }

    /**
     * 群发消息
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
}
