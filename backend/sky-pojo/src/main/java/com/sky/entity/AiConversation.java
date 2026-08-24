package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI客服会话
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    //会话标题，取自第一条用户消息的前若干字
    private String title;

    //更早历史的滚动摘要，为空表示这个会话还没长到需要裁剪（消息数没超过AiChatServiceImpl.RECENT_KEEP_MESSAGES）
    private String summary;

    //summary已经融合到了哪一条ai_message为止（该id及更早的消息都已经被摘要覆盖，不用原文再发给OpenAI）
    private Long summarizedThroughMessageId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
