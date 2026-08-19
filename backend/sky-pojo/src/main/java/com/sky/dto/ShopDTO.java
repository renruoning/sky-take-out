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

}
