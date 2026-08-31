package com.sky.task;

import com.alibaba.fastjson.JSON;
import com.sky.client.ProductClient;
import com.sky.dto.StockChangeItemDTO;
import com.sky.entity.StockCompensationLog;
import com.sky.mapper.StockCompensationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 下单失败后的库存补偿兜底：OrderServiceImpl.persistPendingCompensation落地的待补偿记录，
 * 如果同步重试（restoreStockWithRetry）3次都失败、或者进程在补偿完成之前就崩溃了，
 * 这里定期把还没完成的记录重新跑一遍，直到成功或者被人工介入处理——这是这笔补偿最后的兜底，
 * 不依赖任何还活着的调用方线程
 */
@Component
@Slf4j
public class StockCompensationTask {

    private static final int BATCH_LIMIT = 50;

    private final StockCompensationLogMapper stockCompensationLogMapper;
    private final ProductClient productClient;

    StockCompensationTask(StockCompensationLogMapper stockCompensationLogMapper, ProductClient productClient) {
        this.stockCompensationLogMapper = stockCompensationLogMapper;
        this.productClient = productClient;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryPendingCompensations() {
        List<StockCompensationLog> pending = stockCompensationLogMapper.getPending(BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }
        log.info("库存补偿兜底任务：发现{}条待补偿记录，开始重试", pending.size());
        for (StockCompensationLog item : pending) {
            retryOne(item);
        }
    }

    /**
     * 先真正调用product-service成功、再标记DONE——跟OrderMapper.markStockRestored反过来的
     * 顺序是刻意的（见StockCompensationLogMapper.markDone注释）。代价是如果调用成功后紧接着
     * markDone本身失败（比如数据库恰好在这一瞬间抖了一下），下一轮定时任务会把这条记录当成
     * 还没完成、重新调用一次恢复库存，导致这笔库存被多算一次——这是个已知的、窗口极窄的残余风险，
     * 跟"宁可漏、不可重复"这个优先级相反，是因为这里的"漏"（永远不重试）后果更严重：
     * 这张表本来就是补偿的最后一道防线，如果标记完成放在调用之前，一次失败就永久失去重试机会，
     * 比偶尔多算一次库存的代价更大
     */
    private void retryOne(StockCompensationLog logEntry) {
        try {
            List<StockChangeItemDTO> items = JSON.parseArray(logEntry.getStockItems(), StockChangeItemDTO.class);
            productClient.restoreStock(items);
            stockCompensationLogMapper.markDone(logEntry.getId());
        } catch (Exception e) {
            log.error("库存补偿兜底任务重试记录{}仍然失败，stockItems={}，等待下一轮重试",
                    logEntry.getId(), logEntry.getStockItems(), e);
        }
    }
}
