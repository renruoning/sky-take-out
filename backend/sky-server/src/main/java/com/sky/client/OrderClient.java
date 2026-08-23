package com.sky.client;

import com.sky.dto.DailyOrderStatDTO;
import com.sky.dto.GoodsSalesDTO;
import com.sky.result.Result;
import com.sky.vo.OrderBusinessStatVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通过Nacos服务发现直连sky-order-service的内部接口（不走Gateway）。
 * order/order_detail/shop这次搬去了order-service，ReportServiceImpl/WorkspaceServiceImpl
 * 原来直查OrderMapper/OrderDetailMapper做的聚合统计，改成调这几个聚合端点——
 * 每个端点在order-service内部收敛了原来多次mapper调用，避免N次Feign往返代替N次本地mapper调用。
 */
@FeignClient(name = "sky-order-service")
public interface OrderClient {

    @GetMapping("/internal/order/{id}")
    Result<OrderSummaryVO> getOrderSummary(@PathVariable("id") Long id);

    @GetMapping("/internal/order/business-stats")
    Result<OrderBusinessStatVO> getBusinessStats(@RequestParam("shopId") Long shopId,
                                                  @RequestParam("begin") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime begin,
                                                  @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end);

    @GetMapping("/internal/order/overview")
    Result<OrderOverViewVO> getOrderOverview(@RequestParam("shopId") Long shopId);

    @GetMapping("/internal/order/daily-stats")
    Result<List<DailyOrderStatDTO>> getDailyStats(@RequestParam("shopId") Long shopId,
                                                   @RequestParam("begin") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                                   @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end);

    @GetMapping("/internal/order/sales-top10")
    Result<List<GoodsSalesDTO>> getSalesTop10(@RequestParam("shopId") Long shopId,
                                               @RequestParam("begin") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                               @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end);
}
