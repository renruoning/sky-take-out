package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户端店铺列表展示对象：店铺基础信息 + 评价聚合数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String address;

    private String phone;

    private Integer businessType;

    private Integer secondaryBusinessType;

    private Double avgRating;

    private Integer reviewCount;
}
