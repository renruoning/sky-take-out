package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户提交订单评价
 */
@Data
public class ReviewDTO implements Serializable {

    //关联订单id
    private Long orderId;

    //1-5星
    private Integer rating;

    private String content;

    //图片url，逗号分隔
    private String images;
}
