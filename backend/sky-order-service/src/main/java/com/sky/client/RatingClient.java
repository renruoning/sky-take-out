package com.sky.client;

import com.sky.result.Result;
import com.sky.vo.ShopRatingSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * sky-server第一次反过来当Feign的调用方（之前发票/评价服务都是调sky-server，这次是sky-server调评价服务）。
 * 一次批量请求拿到店铺列表里所有店铺的评分聚合数据，而不是对每个店铺单独发一次请求——
 * ShopController.list()是全站访问量最大的接口，逐店铺RPC会是货真价实的N+1
 */
@FeignClient(name = "sky-review-service")
public interface RatingClient {

    @PostMapping("/internal/rating-summary/batch")
    Result<Map<Long, ShopRatingSummaryVO>> getRatingSummaryBatch(@RequestBody List<Long> shopIds);
}
