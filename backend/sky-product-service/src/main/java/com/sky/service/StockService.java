package com.sky.service;

import com.sky.dto.StockChangeItemDTO;

import java.util.List;

public interface StockService {

    /**
     * 批量扣减库存，同一批条目要么全部扣成功，要么一个都不扣（其中一项库存不足就整体回滚）
     * @param items 扣减条目列表
     */
    void deductStock(List<StockChangeItemDTO> items);

    /**
     * 批量恢复库存（订单取消时的补偿操作）
     * @param items 恢复条目列表
     */
    void restoreStock(List<StockChangeItemDTO> items);
}
