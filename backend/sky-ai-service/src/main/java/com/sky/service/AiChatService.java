package com.sky.service;

import com.sky.dto.AiChatDTO;
import com.sky.entity.AiConversation;
import com.sky.entity.AiMessage;
import com.sky.vo.AiChatVO;

import java.util.List;

public interface AiChatService {

    /**
     * 发送一条消息，返回AI回复
     * @param aiChatDTO
     * @return
     */
    AiChatVO chat(AiChatDTO aiChatDTO);

    /**
     * 查询当前用户的会话列表
     * @return
     */
    List<AiConversation> listConversations();

    /**
     * 查询某会话的完整消息记录（校验归属）
     * @param conversationId
     * @return
     */
    List<AiMessage> listMessages(Long conversationId);
}
