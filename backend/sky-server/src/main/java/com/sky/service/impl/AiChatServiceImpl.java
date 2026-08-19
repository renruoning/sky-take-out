package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.AiChatDTO;
import com.sky.entity.AiConversation;
import com.sky.entity.AiMessage;
import com.sky.exception.AiChatBusinessException;
import com.sky.mapper.AiConversationMapper;
import com.sky.mapper.AiMessageMapper;
import com.sky.properties.OpenAiProperties;
import com.sky.service.AiChatService;
import com.sky.utils.OpenAiUtil;
import com.sky.vo.AiChatVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String SYSTEM_PROMPT =
            "你是 Sky Take Out 平台的智能客服助手。这是一个多品类本地生活服务平台，商户覆盖餐饮外卖、医药健康、蔬果生鲜、鲜花绿植几类业务。" +
            "请友好、简洁地回答用户关于平台使用方式的问题（如何下单、如何选店、如何评价等）。" +
            "你不掌握用户的具体订单、库存、账户等实时数据，遇到这类问题要如实说明你查不到，并引导用户去对应功能页面自行查看，不要编造数据。";

    private static final int TITLE_MAX_LENGTH = 20;

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final OpenAiProperties openAiProperties;

    AiChatServiceImpl(AiConversationMapper aiConversationMapper, AiMessageMapper aiMessageMapper, OpenAiProperties openAiProperties) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.openAiProperties = openAiProperties;
    }

    public AiChatVO chat(AiChatDTO aiChatDTO) {
        Long userId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now();

        AiConversation conversation;
        if (aiChatDTO.getConversationId() == null) {
            conversation = AiConversation.builder()
                    .userId(userId)
                    .title(buildTitle(aiChatDTO.getMessage()))
                    .createTime(now)
                    .updateTime(now)
                    .build();
            aiConversationMapper.insert(conversation);
        } else {
            conversation = aiConversationMapper.getById(aiChatDTO.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new AiChatBusinessException(MessageConstant.AI_CONVERSATION_NOT_FOUND);
            }
        }

        // 记录用户消息
        aiMessageMapper.insert(AiMessage.builder()
                .conversationId(conversation.getId())
                .role(AiMessage.ROLE_USER)
                .content(aiChatDTO.getMessage())
                .createTime(now)
                .build());

        // 拼历史上下文（含刚插入的这一条），调用OpenAI
        List<AiMessage> history = aiMessageMapper.listByConversationId(conversation.getId());
        List<Map<String, String>> apiMessages = new ArrayList<>();
        apiMessages.add(buildMessage("system", SYSTEM_PROMPT));
        apiMessages.addAll(history.stream()
                .map(m -> buildMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList()));

        String reply;
        try {
            reply = OpenAiUtil.chat(openAiProperties.getApiKey(), openAiProperties.getModel(), openAiProperties.getBaseUrl(), apiMessages);
        } catch (Exception e) {
            log.error("调用OpenAI接口失败", e);
            throw new AiChatBusinessException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }

        LocalDateTime replyTime = LocalDateTime.now();
        aiMessageMapper.insert(AiMessage.builder()
                .conversationId(conversation.getId())
                .role(AiMessage.ROLE_ASSISTANT)
                .content(reply)
                .createTime(replyTime)
                .build());
        aiConversationMapper.updateTime(AiConversation.builder().id(conversation.getId()).updateTime(replyTime).build());

        return AiChatVO.builder()
                .conversationId(conversation.getId())
                .reply(reply)
                .build();
    }

    public List<AiConversation> listConversations() {
        return aiConversationMapper.listByUserId(BaseContext.getCurrentId());
    }

    public List<AiMessage> listMessages(Long conversationId) {
        AiConversation conversation = aiConversationMapper.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(BaseContext.getCurrentId())) {
            throw new AiChatBusinessException(MessageConstant.AI_CONVERSATION_NOT_FOUND);
        }
        return aiMessageMapper.listByConversationId(conversationId);
    }

    private String buildTitle(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > TITLE_MAX_LENGTH ? message.substring(0, TITLE_MAX_LENGTH) : message;
    }

    private Map<String, String> buildMessage(String role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }
}
