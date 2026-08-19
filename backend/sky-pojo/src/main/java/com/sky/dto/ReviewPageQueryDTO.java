package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ReviewPageQueryDTO implements Serializable {

    //页码
    private int page;

    //每页记录数
    private int pageSize;

    //所属店铺id：用户端由前端显式传入；管理端由服务层从当前登录员工上下文填充（为null表示平台超管，看全平台）
    private Long shopId;

    //按星级筛选，可选
    private Integer rating;
}
