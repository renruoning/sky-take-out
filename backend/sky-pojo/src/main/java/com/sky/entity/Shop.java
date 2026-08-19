package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺（商户）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //店铺名称
    private String name;

    //地址
    private String address;

    //联系电话
    private String phone;

    //状态 0:禁用 1:启用
    private Integer status;

    //主营业类型 1=餐饮 2=医药 3=蔬果 4=花卉
    private Integer businessType;

    //副营业类型（可为空），取值同businessType，不能与主营业类型相同
    private Integer secondaryBusinessType;

    //主/副营业类型最近一次被修改的时间，用于限制一年内最多改一次
    private LocalDateTime businessTypeUpdatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;
}
