package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    private String username;

    private String name;

    private String phone;

    private String sex;

    private String idNumber;

    //目标店铺id：仅平台超管新增员工时必须指定；店铺员工自己新增下属时忽略该字段，强制归属自己所在店铺
    private Long shopId;

}
