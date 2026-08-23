package com.sky.controller.internal;

import com.sky.result.Result;
import com.sky.service.ReviewService;
import com.sky.vo.ShopRatingSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供sky-server批量查询店铺评分用（首页店铺列表要展示每个店铺的均分/评价数），一次RPC覆盖一批店铺，
 * 不是让sky-server对每个店铺单独发一次请求——路径不在/admin或/user下，靠Gateway的
 * InternalPathBlockingFilter挡住外部访问，跟/internal/order/{id}是同一套鉴权思路
 */
@RestController
@Slf4j
public class InternalRatingController {

    private final ReviewService reviewService;

    InternalRatingController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/internal/rating-summary/batch")
    public Result<Map<Long, ShopRatingSummaryVO>> batch(@RequestBody List<Long> shopIds) {
        return Result.success(reviewService.getShopRatingSummaryBatch(shopIds));
    }
}
