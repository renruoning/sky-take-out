package com.sky.controller.user;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.client.RatingClient;
import com.sky.entity.Shop;
import com.sky.result.Result;
import com.sky.service.ShopService;
import com.sky.vo.ShopRatingSummaryVO;
import com.sky.vo.ShopVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@RestController("userShopController")
@RequestMapping("/user/shop")
@Slf4j
@RequiredArgsConstructor
public class ShopController {

    private static final String KEY_PREFIX = "SHOP_STATUS:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShopService shopService;
    private final RatingClient ratingClient;

    @GetMapping("/list")
    public Result<List<ShopVO>> list(Integer businessType) {
        List<Shop> shops = shopService.listActive(businessType);
        if (shops.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 一次批量RPC拿到这批店铺的评分聚合数据，不是对每个店铺单独调一次评价服务——
        // 这是首页最高频接口，逐店铺RPC是真实的N+1；评价服务不可用时fail-open成"暂无评分"，
        // 不能因为评价服务挂了整个首页选店铺都打不开
        List<Long> shopIds = shops.stream().map(Shop::getId).collect(Collectors.toList());
        Map<Long, ShopRatingSummaryVO> ratingMap;
        try {
            com.sky.result.Result<Map<Long, ShopRatingSummaryVO>> ratingResult = ratingClient.getRatingSummaryBatch(shopIds);
            ratingMap = (ratingResult != null && ratingResult.getCode() != null && ratingResult.getCode() == 1
                    && ratingResult.getData() != null) ? ratingResult.getData() : new HashMap<>();
        } catch (Exception e) {
            log.error("批量查询店铺评分失败（sky-review-service不可达），本次店铺列表不展示评分（fail-open）", e);
            ratingMap = new HashMap<>();
        }
        Map<Long, ShopRatingSummaryVO> finalRatingMap = ratingMap;

        List<ShopVO> result = shops.stream().map(shop -> {
            ShopRatingSummaryVO summary = finalRatingMap.get(shop.getId());
            return ShopVO.builder()
                    .id(shop.getId())
                    .name(shop.getName())
                    .address(shop.getAddress())
                    .phone(shop.getPhone())
                    .businessType(shop.getBusinessType())
                    .secondaryBusinessType(shop.getSecondaryBusinessType())
                    .avgRating(summary != null ? summary.getAvgRating() : null)
                    .reviewCount(summary != null ? summary.getReviewCount() : 0)
                    .build();
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @GetMapping("/status")
    public Result<Integer> getStatus(Long shopId){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY_PREFIX + shopId);
        log.info("获取到店铺{}的营业状态为：{}", shopId, status==1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
