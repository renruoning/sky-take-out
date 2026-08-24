package com.sky.controller.user;

import com.sky.annotation.RateLimit;
import com.sky.cache.LogicalExpireCache;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.enumeration.RateLimitKeyType;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
public class DishController {

    public static final String DISH_CACHE_PREFIX = "dishCache::";
    private static final Duration DISH_CACHE_LOGICAL_TTL = Duration.ofSeconds(60);

    private final DishService dishService;
    private final LogicalExpireCache logicalExpireCache;

    DishController(DishService dishService, LogicalExpireCache logicalExpireCache) {
        this.dishService = dishService;
        this.logicalExpireCache = logicalExpireCache;
    }

    /**
     * 根据分类id查询菜品——首页级高并发热点接口，用逻辑过期缓存防止某个热门分类的key过期瞬间被并发穿透到DB
     * （见LogicalExpireCache类注释）；categoryId本身是category表的全局唯一主键，不会跨店铺撞key
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @RateLimit(keyType = RateLimitKeyType.IP, limit = 120, windowSeconds = 60, name = "menu_query",
            message = "请求过于频繁，请稍后再试")
    public Result<List<DishVO>> list(Long categoryId, Long shopId) {
        String cacheKey = DISH_CACHE_PREFIX + categoryId;
        List<DishVO> list = logicalExpireCache.get(cacheKey, DISH_CACHE_LOGICAL_TTL,
                new TypeReference<List<DishVO>>() {
                }, () -> {
                    Dish dish = new Dish();
                    dish.setCategoryId(categoryId);
                    dish.setShopId(shopId);
                    dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
                    return dishService.listWithFlavor(dish);
                });

        return Result.success(list);
    }

}
