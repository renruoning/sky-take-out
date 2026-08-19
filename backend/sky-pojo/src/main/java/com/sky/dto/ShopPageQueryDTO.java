package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopPageQueryDTO implements Serializable {

    //页码
    private int page;

    //每页记录数
    private int pageSize;

    //店铺名称
    private String name;

    //状态 0:禁用 1:启用
    private Integer status;

    //主营业类型 1=餐饮 2=医药 3=蔬果 4=花卉
    private Integer businessType;

}
