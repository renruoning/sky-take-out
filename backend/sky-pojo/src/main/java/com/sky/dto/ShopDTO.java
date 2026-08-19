package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopDTO implements Serializable {

    //主键（编辑时必传）
    private Long id;

    //店铺名称
    private String name;

    //地址
    private String address;

    //联系电话
    private String phone;

    //主营业类型 1=餐饮 2=医药 3=蔬果 4=花卉（新增店铺时必填）
    private Integer businessType;

    //副营业类型（可为空），取值同businessType，不能与主营业类型相同
    private Integer secondaryBusinessType;

}
