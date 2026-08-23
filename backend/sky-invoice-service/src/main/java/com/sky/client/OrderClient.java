package com.sky.client;

import com.sky.result.Result;
import com.sky.vo.OrderSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 通过Nacos服务发现直连sky-order-service的内部接口（/internal/order/{id}，不走Gateway）——
 * 订单/店铺这次拆成了独立的sky-order-service，目标服务名从sky-server改过来，接口本身不变
 */
@FeignClient(name = "sky-order-service")
public interface OrderClient {

    @GetMapping("/internal/order/{id}")
    Result<OrderSummaryVO> getOrderSummary(@PathVariable("id") Long id);
}
