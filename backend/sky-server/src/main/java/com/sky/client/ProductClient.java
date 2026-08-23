package com.sky.client;

import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务拆出去之后，购物车"加菜品/加套餐"（要查名称/图片/价格填充快照）和管理端工作台首页
 * （要查在售/停售统计）都要靠这个Feign客户端问sky-product-service要数据
 */
@FeignClient(name = "sky-product-service")
public interface ProductClient {

    @GetMapping("/internal/dish/{id}")
    Result<Dish> getDish(@PathVariable("id") Long id);

    @GetMapping("/internal/setmeal/{id}")
    Result<Setmeal> getSetmeal(@PathVariable("id") Long id);

    @GetMapping("/internal/dish/overview")
    Result<DishOverViewVO> getDishOverview(@RequestParam("shopId") Long shopId);

    @GetMapping("/internal/setmeal/overview")
    Result<SetmealOverViewVO> getSetmealOverview(@RequestParam("shopId") Long shopId);
}
