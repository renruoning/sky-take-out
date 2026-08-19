package com.sky.controller.admin;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShopDTO;
import com.sky.dto.ShopPageQueryDTO;
import com.sky.exception.ShopBusinessException;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.ShopService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;



@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "店铺相关接口")
@Slf4j
@RequiredArgsConstructor
public class ShopController {

    private static final String KEY_PREFIX = "SHOP_STATUS:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShopService shopService;

    @PutMapping("/{status}")
    @ApiOperation("设置当前登录员工所属店铺的营业状态")
    public Result<?> setStatus(@PathVariable Integer status){
        Long shopId = BaseContext.getCurrentShopId();
        if (shopId == null) {
            throw new ShopBusinessException(MessageConstant.SHOP_SCOPED_ONLY);
        }
        log.info("设置店铺{}的营业状态为：{}", shopId, status==1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(KEY_PREFIX + shopId, status);
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("获取当前登录员工所属店铺的营业状态")
    public Result<Integer> getStatus(){
        Long shopId = BaseContext.getCurrentShopId();
        if (shopId == null) {
            throw new ShopBusinessException(MessageConstant.SHOP_SCOPED_ONLY);
        }
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY_PREFIX + shopId);
        log.info("获取到店铺{}的营业状态为：{}", shopId, status==1 ? "营业中" : "打烊中");
        return Result.success(status);
    }

    /**
     * 新增店铺（平台超管专用）
     */
    @PostMapping("/platform")
    @ApiOperation("新增店铺（平台超管）")
    public Result<?> save(@RequestBody ShopDTO shopDTO) {
        shopService.save(shopDTO);
        return Result.success();
    }

    /**
     * 分页查询店铺（平台超管专用）
     */
    @GetMapping("/platform/page")
    @ApiOperation("分页查询店铺（平台超管）")
    public Result<PageResult> page(ShopPageQueryDTO shopPageQueryDTO) {
        PageResult pageResult = shopService.pageQuery(shopPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 修改店铺信息（平台超管专用）
     */
    @PutMapping("/platform")
    @ApiOperation("修改店铺信息（平台超管）")
    public Result<?> update(@RequestBody ShopDTO shopDTO) {
        shopService.update(shopDTO);
        return Result.success();
    }

    /**
     * 启用、禁用店铺（平台超管专用）
     */
    @PostMapping("/platform/status/{status}")
    @ApiOperation("启用禁用店铺（平台超管）")
    public Result<?> startOrStop(@PathVariable Integer status, Long id) {
        shopService.startOrStop(status, id);
        return Result.success();
    }
}
