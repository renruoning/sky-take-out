package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeePageQueryDTO implements Serializable {

    //员工姓名
    private String name;

    //页码
    private int page;

    //每页显示记录数
    private int pageSize;

    //所属店铺id（由服务层从当前登录员工上下文填充；为null表示平台超管，查看所有店铺）
    private Long shopId;

}
