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

    // 单店铺查询（getShopRatingSummary）场景下调用方已经知道shopId，不需要这个字段；
    // 批量查询（getShopRatingSummaryBatch）场景下这个字段用来把结果和shopId对应起来
    private Long shopId;

    private Double avgRating;

    private Integer reviewCount;
}
