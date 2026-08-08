package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    private final DishService dishService;
    private final RedisTemplate<String, Object> redisTemplate;

    DishController(DishService dishService, RedisTemplate<String, Object> redisTemplate) {
        this.dishService = dishService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    // TODO: 查询完全依赖redis，未做降级处理——redis异常/不可用时该接口会直接报错，而不是回退查数据库；
    // 另外所有key固定1小时过期，可能引发缓存雪崩，可考虑给过期时间加随机抖动
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        // 先从redis中查询缓存，key的格式为 dish_分类id
        String key = "dish_" + categoryId;
        @SuppressWarnings("unchecked")
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        // 空列表也是一次有效的缓存结果（避免缓存穿透），不能当作未命中
        if (list != null) {
            return Result.success(list);
        }

        // redis中没有缓存，查询数据库，并将查询结果放入redis
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key, list, 1, TimeUnit.HOURS);

        return Result.success(list);
    }

}
