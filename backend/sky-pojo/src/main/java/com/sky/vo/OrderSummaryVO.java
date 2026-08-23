package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单摘要，供服务间RPC调用返回（比如sky-invoice-service/sky-review-service通过Feign查sky-server要用到的
 * 订单/店铺/下单人信息），不是直接暴露Orders实体——只带调用方真正需要的字段，避免服务间接口把内部实体结构泄漏出去。
 * 这是给内部服务用的"订单信息大礼包"，不是为某一个消费者量身定制的，不同消费者用得到的字段不一样很正常
 * （比如发票只用payStatus不用status，评价只用status不用payStatus）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long shopId;

    private String number;

    //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消，对应sky-server端Orders的状态常量
    private Integer status;

    private Integer payStatus;

    private BigDecimal amount;

    private LocalDateTime orderTime;

    private String shopName;

    private String userName;

    private String userAvatar;
}
