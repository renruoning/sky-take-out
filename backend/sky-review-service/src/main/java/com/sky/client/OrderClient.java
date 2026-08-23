package com.sky.client;

import com.sky.result.Result;
import com.sky.vo.OrderSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 跟sky-invoice-service里那个OrderClient一样，通过Nacos服务发现直连sky-order-service的内部接口
 * （订单/店铺这次拆成了独立服务，目标服务名从sky-server改过来）。评价服务用得到的是status
 * （订单状态，判断是否COMPLETED）和userName/userAvatar（评价快照），跟发票服务用payStatus/shopName
 * 不完全一样，但复用的是同一个/internal/order/{id}端点
 */
@FeignClient(name = "sky-order-service")
public interface OrderClient {

    @GetMapping("/internal/order/{id}")
    Result<OrderSummaryVO> getOrderSummary(@PathVariable("id") Long id);
}
