package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工作台"营业数据"用的原始订单统计数字（总订单数/有效订单数/营业额），
 * 由sky-order-service一次查询算出返回，调用方（sky-server的WorkspaceServiceImpl）
 * 在本地拿这三个数算订单完成率/平均客单价，避免把纯展示层的计算逻辑也下沉到内部接口里
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBusinessStatVO implements Serializable {
    //总订单数
    private Integer totalOrderCount;

    //有效订单数（已完成）
    private Integer validOrderCount;

    //营业额（已完成订单实收金额之和）
    private Double turnover;
}
