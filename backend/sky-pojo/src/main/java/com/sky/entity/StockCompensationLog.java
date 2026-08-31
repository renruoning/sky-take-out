package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 下单失败后的库存补偿记录：扣库存成功但建单失败时，在反向调用product-service恢复库存之前，
 * 先把"需要恢复哪些库存"落到这张表里（独立事务提交，不随submit()本身的事务一起回滚），
 * 这样即使进程在补偿完成之前崩溃、或者同步重试耗尽仍失败，定时任务也能靠这条记录把补偿
 * 重新跑起来，不会像原来那样一旦失败就只剩一条日志、彻底没有重试的机会
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCompensationLog implements Serializable {

    public static final Integer PENDING = 0;
    public static final Integer DONE = 1;

    private Long id;

    /**
     * 需要恢复的库存明细，JSON序列化的List<StockChangeItemDTO>
     */
    private String stockItems;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
