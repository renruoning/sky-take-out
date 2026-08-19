package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 店铺评价聚合数据（平均分+评价数），用于店铺列表展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopRatingSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Double avgRating;

    private Integer reviewCount;
}
