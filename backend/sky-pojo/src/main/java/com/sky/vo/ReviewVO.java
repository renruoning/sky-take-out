package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNumber;

    private Long shopId;

    private Long userId;

    private String userName;

    private String userAvatar;

    private Integer rating;

    private String content;

    private String images;

    private String reply;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;
}
