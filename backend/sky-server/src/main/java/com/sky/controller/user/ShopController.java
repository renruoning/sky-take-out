package com.sky.controller.user;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.entity.Shop;
import com.sky.result.Result;
import com.sky.service.ShopService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;



@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
@RequiredArgsConstructor
public class ShopController {

    private static final String KEY_PREFIX = "SHOP_STATUS:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShopService shopService;

    @GetMapping("/list")
    @ApiOperation("查询所有营业中的店铺，供用户端选店铺使用")
    public Result<List<Shop>> list() {
        return Result.success(shopService.listActive());
    }

    @GetMapping("/status")
    @ApiOperation("获取指定店铺的营业状态")
    public Result<Integer> getStatus(Long shopId){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY_PREFIX + shopId);
        log.info("获取到店铺{}的营业状态为：{}", shopId, status==1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
