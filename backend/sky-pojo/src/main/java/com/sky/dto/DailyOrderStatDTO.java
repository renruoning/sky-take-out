package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 按天分组统计的订单数据（营业额/订单数），用于报表按天聚合查询，
 * 避免按天循环单独发SQL造成的N+1查询问题
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailyOrderStatDTO implements Serializable {

    private LocalDate date;

    //当天订单总数
    private Integer totalOrderCount;

    //当天有效订单数（已完成）
    private Integer validOrderCount;

    //当天营业额（已完成订单实收金额之和）
    private Double turnover;
}
