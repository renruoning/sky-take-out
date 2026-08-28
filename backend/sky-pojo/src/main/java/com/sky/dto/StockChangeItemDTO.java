package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存扣减/恢复的单项条目，order-service下单/取消订单时通过Feign传给
 * sky-product-service。dishId/setmealId二选一，另一个为null，number统一用正数表示，
 * 具体是加还是减由调用的是deduct接口还是restore接口决定，条目本身不关心方向。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockChangeItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long dishId;

    private Long setmealId;

    private Integer number;
}
