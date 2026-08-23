package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票申请
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Integer TYPE_PERSONAL = 1;
    public static final Integer TYPE_COMPANY = 2;

    private Long id;

    //关联订单，一单只能开一张发票
    private Long orderId;

    private Long userId;

    private Long shopId;

    //发票抬头
    private String title;

    //抬头类型 1个人 2单位
    private Integer invoiceType;

    //纳税人识别号，单位抬头必填
    private String taxNumber;

    //接收邮箱，可选
    private String email;

    //冗余存订单金额，避免发票和订单后续变化产生歧义
    private BigDecimal amount;

    //以下三个是申请时从订单/店铺快照下来的展示字段（发票服务拆成独立库之后没法再跟orders/shop表JOIN，
    //只能在申请这一刻通过RPC拿到订单摘要后存成自己的字段；发票本来就该是不可变的历史凭证，快照语义上也更合适）
    private String orderNumber;

    private LocalDateTime orderTime;

    private String shopName;

    private LocalDateTime createTime;
}
