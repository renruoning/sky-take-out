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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
