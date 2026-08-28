package com.sky.client;

import com.sky.dto.StockChangeItemDTO;
import com.sky.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 下单扣库存/取消订单恢复库存，靠这个Feign客户端问sky-product-service要。
 * 库存不足时对方返回的是Result.code!=1（不是Feign异常），见InternalProductController.deductStock
 */
@FeignClient(name = "sky-product-service")
public interface ProductClient {

    @PostMapping("/internal/stock/deduct")
    Result<String> deductStock(@RequestBody List<StockChangeItemDTO> items);

    @PostMapping("/internal/stock/restore")
    Result<String> restoreStock(@RequestBody List<StockChangeItemDTO> items);
}
