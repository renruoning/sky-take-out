package com.sky.controller.internal;

import com.sky.constant.StatusConstant;
import com.sky.dto.StockChangeItemDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.Result;
import com.sky.service.StockService;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供其它微服务通过Feign调用的内部接口，跟sky-server/sky-review-service里那几个/internal/**是同一套鉴权思路——
 * 靠Gateway的InternalPathBlockingFilter挡住外部访问，不带用户/员工token
 */
@RestController
@Slf4j
public class InternalProductController {

    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;
    private final StockService stockService;

    InternalProductController(DishMapper dishMapper, SetmealMapper setmealMapper, StockService stockService) {
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
        this.stockService = stockService;
    }

    /**
     * 供sky-server的购物车"加菜品"用：只查id，不做归属校验——购物车加购时的shopId一致性
     * 由sky-server自己的购物车逻辑校验，这里只是单纯的数据查询
     */
    @GetMapping("/internal/dish/{id}")
    public Result<Dish> getDish(@PathVariable Long id) {
        return Result.success(dishMapper.getById(id));
    }

    @GetMapping("/internal/setmeal/{id}")
    public Result<Setmeal> getSetmeal(@PathVariable Long id) {
        return Result.success(setmealMapper.getById(id));
    }

    /**
     * 供sky-server的管理端工作台首页用：在售/停售菜品数量统计
     */
    @GetMapping("/internal/dish/overview")
    public Result<DishOverViewVO> getDishOverview(@RequestParam Long shopId) {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(map);

        return Result.success(DishOverViewVO.builder().sold(sold).discontinued(discontinued).build());
    }

    @GetMapping("/internal/setmeal/overview")
    public Result<SetmealOverViewVO> getSetmealOverview(@RequestParam Long shopId) {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = setmealMapper.countByMap(map);

        return Result.success(SetmealOverViewVO.builder().sold(sold).discontinued(discontinued).build());
    }

    /**
     * 供order-service下单时扣减库存：库存不够会抛StockBusinessException，被GlobalExceptionHandler
     * 转成Result.error(msg)返回（HTTP 200），不是Feign异常——调用方靠code!=1判断失败，见OrderServiceImpl.submit()
     */
    @PostMapping("/internal/stock/deduct")
    public Result<String> deductStock(@RequestBody List<StockChangeItemDTO> items) {
        stockService.deductStock(items);
        return Result.success();
    }

    /**
     * 供order-service订单取消时恢复库存（补偿操作）
     */
    @PostMapping("/internal/stock/restore")
    public Result<String> restoreStock(@RequestBody List<StockChangeItemDTO> items) {
        stockService.restoreStock(items);
        return Result.success();
    }
}
