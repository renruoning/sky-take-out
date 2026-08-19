package com.sky.controller.user;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.entity.Shop;
import com.sky.result.Result;
import com.sky.service.ReviewService;
import com.sky.service.ShopService;
import com.sky.vo.ShopRatingSummaryVO;
import com.sky.vo.ShopVO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;



@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
@RequiredArgsConstructor
public class ShopController {

    private static final String KEY_PREFIX = "SHOP_STATUS:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShopService shopService;
    private final ReviewService reviewService;

    @GetMapping("/list")
    @ApiOperation("查询营业中的店铺，供用户端选店铺使用；businessType按主/副营业类型过滤，不传则返回全部")
    public Result<List<ShopVO>> list(Integer businessType) {
        List<Shop> shops = shopService.listActive(businessType);
        List<ShopVO> result = shops.stream().map(shop -> {
            ShopRatingSummaryVO summary = reviewService.getShopRatingSummary(shop.getId());
            return ShopVO.builder()
                    .id(shop.getId())
                    .name(shop.getName())
                    .address(shop.getAddress())
                    .phone(shop.getPhone())
                    .businessType(shop.getBusinessType())
                    .secondaryBusinessType(shop.getSecondaryBusinessType())
                    .avgRating(summary.getAvgRating())
                    .reviewCount(summary.getReviewCount())
                    .build();
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @GetMapping("/status")
    @ApiOperation("获取指定店铺的营业状态")
    public Result<Integer> getStatus(Long shopId){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY_PREFIX + shopId);
        log.info("获取到店铺{}的营业状态为：{}", shopId, status==1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
