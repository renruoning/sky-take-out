package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI客服消息记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private Long id;

    private Long conversationId;

    //user 或 assistant
    private String role;

    private String content;

    private LocalDateTime createTime;
}
