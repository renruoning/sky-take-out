package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiChatDTO implements Serializable {

    //会话id，为空表示新建会话
    private Long conversationId;

    //用户发送的消息内容
    private String message;
}
