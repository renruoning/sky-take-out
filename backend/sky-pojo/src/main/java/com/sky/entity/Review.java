package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单评价（一单一评）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //关联订单，一单只能评一次
    private Long orderId;

    //所属店铺id（冗余自订单，避免每次查评价都要join orders）
    private Long shopId;

    //评价人
    private Long userId;

    //1-5星
    private Integer rating;

    private String content;

    //图片url，逗号分隔
    private String images;

    //商家回复
    private String reply;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;
}
