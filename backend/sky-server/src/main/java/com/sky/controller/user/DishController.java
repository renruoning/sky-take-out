package com.sky.controller.user;

import com.sky.annotation.RateLimit;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.enumeration.RateLimitKeyType;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    private final DishService dishService;

    DishController(DishService dishService) {
        this.dishService = dishService;
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    @Cacheable(cacheNames = "dishCache", key = "#categoryId")
    @RateLimit(keyType = RateLimitKeyType.IP, limit = 120, windowSeconds = 60, name = "menu_query",
            message = "请求过于频繁，请稍后再试")
    public Result<List<DishVO>> list(Long categoryId, Long shopId) {
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setShopId(shopId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        List<DishVO> list = dishService.listWithFlavor(dish);

        return Result.success(list);
    }

}
