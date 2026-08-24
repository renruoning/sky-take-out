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

    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是对话摘要助手。请把提供的对话内容浓缩成一段不超过200字的摘要，保留用户提到的、对后续对话有用的关键信息" +
            "（比如称呼、偏好、已经问过/答过的问题要点），不要输出摘要以外的任何内容。如果给了\"此前摘要\"，" +
            "请在其基础上融合新内容，输出一份完整的、更新后的摘要，而不是只写新增的部分。";

    private static final int TITLE_MAX_LENGTH = 20;

    // 会话消息数超过这个数才开始裁剪——普通几轮的对话完全不受影响，只有真的聊得很长时才会触发，
    // 省下来的是"每一轮都要把全部历史原文重新发一遍"这部分随对话变长而线性增长的token开销
    private static final int RECENT_KEEP_MESSAGES = 16;

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
        apiMessages.addAll(buildContextMessages(conversation, history));

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

    /**
     * 把DB里的完整历史转成发给OpenAI的消息列表，超过RECENT_KEEP_MESSAGES条时做裁剪：
     * 更早的部分不再原文发送，折叠进一段滚动摘要（不够就直接全量发，行为跟裁剪前完全一样）
     */
    private List<Map<String, String>> buildContextMessages(AiConversation conversation, List<AiMessage> history) {
        if (history.size() <= RECENT_KEEP_MESSAGES) {
            return history.stream().map(m -> buildMessage(m.getRole(), m.getContent())).collect(Collectors.toList());
        }

        int splitIndex = history.size() - RECENT_KEEP_MESSAGES;
        List<AiMessage> older = history.subList(0, splitIndex);
        List<AiMessage> recent = history.subList(splitIndex, history.size());

        Long summarizedThroughId = conversation.getSummarizedThroughMessageId();
        List<AiMessage> newlyOld = older.stream()
                .filter(m -> summarizedThroughId == null || m.getId() > summarizedThroughId)
                .collect(Collectors.toList());

        String summary = conversation.getSummary();
        if (!newlyOld.isEmpty()) {
            // 只有真的摘要成功了才推进summarizedThroughMessageId——半途失败的话newlyOld这批消息的内容
            // 既没进摘要、又快要被挤出"最近N条"窗口，指针不能往前挪，否则这批内容会永久丢失，
            // 下一轮还会把它们当作newlyOld重新尝试摘要
            String updatedSummary = summarizeOlderMessages(summary, newlyOld);
            if (updatedSummary != null) {
                summary = updatedSummary;
                Long newThroughId = older.get(older.size() - 1).getId();
                aiConversationMapper.updateSummary(conversation.getId(), summary, newThroughId);
            }
        }

        List<Map<String, String>> result = new ArrayList<>();
        if (summary != null && !summary.isEmpty()) {
            result.add(buildMessage("system", "以下是这个对话更早部分的摘要，供你参考背景信息：" + summary));
        }
        result.addAll(recent.stream().map(m -> buildMessage(m.getRole(), m.getContent())).collect(Collectors.toList()));
        return result;
    }

    /**
     * 把一批"新变老"的消息折叠进已有摘要，产出更新后的完整摘要。这本身也是一次OpenAI调用，
     * 失败返回null——调用方据此保留旧摘要、不推进summarizedThroughMessageId，
     * 下一轮消息数继续增长后这批内容还会被当作newlyOld重新尝试摘要，不会因为一次失败就永久丢失
     */
    private String summarizeOlderMessages(String existingSummary, List<AiMessage> newlyOld) {
        List<Map<String, String>> apiMessages = new ArrayList<>();
        apiMessages.add(buildMessage("system", SUMMARY_SYSTEM_PROMPT));
        if (existingSummary != null && !existingSummary.isEmpty()) {
            apiMessages.add(buildMessage("user", "此前摘要：" + existingSummary));
        }
        String newContent = newlyOld.stream()
                .map(m -> m.getRole() + "：" + m.getContent())
                .collect(Collectors.joining("\n"));
        apiMessages.add(buildMessage("user", "需要融合进摘要的新对话内容：\n" + newContent));

        try {
            return OpenAiUtil.chat(openAiProperties.getApiKey(), openAiProperties.getModel(), openAiProperties.getBaseUrl(), apiMessages);
        } catch (Exception e) {
            log.error("生成对话摘要失败，本次跳过摘要更新，沿用旧摘要", e);
            return null;
        }
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
