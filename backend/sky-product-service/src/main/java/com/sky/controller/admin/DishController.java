package com.sky.controller.admin;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sky.cache.LogicalExpireCache;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;

import org.springframework.web.bind.annotation.RequestBody;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/dish")
@Slf4j
public class DishController {

    private final DishService dishService;
    private final LogicalExpireCache logicalExpireCache;

    DishController(DishService dishService, LogicalExpireCache logicalExpireCache) {
        this.dishService = dishService;
        this.logicalExpireCache = logicalExpireCache;
    }

    /**
     * 新增菜品
     *
     * @param dishDTO 菜品信息
     * @return 操作结果
     */
    @PostMapping
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品: {}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        logicalExpireCache.evict(com.sky.controller.user.DishController.DISH_CACHE_PREFIX + dishDTO.getCategoryId());
        return Result.success();
    }

    /**
     * 批量删除菜品
     *
     * @param ids 菜品ID列表
     * @return 操作结果
     */
    @DeleteMapping
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除菜品: {}", ids);
        dishService.deleteBatch(ids);
        logicalExpireCache.evictByPrefix(com.sky.controller.user.DishController.DISH_CACHE_PREFIX);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询: {}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据ID查询菜品: {}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    // 修改可能连分类都改了，无法确定只影响哪一个分类的缓存，直接清空所有菜品缓存
    @PutMapping
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品: {}", dishDTO);
        dishService.updateWithFlavor(dishDTO);
        logicalExpireCache.evictByPrefix(com.sky.controller.user.DishController.DISH_CACHE_PREFIX);
        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
    */
    @GetMapping("/list")
    public Result<List<Dish>> list(Long categoryId){
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    /**
     * 起售、停售菜品
     * @param status
     * @param id
     * @return
     */
    // 停售还可能联动停售关联的套餐，影响范围不确定，直接清空所有菜品缓存
    @PostMapping("/status/{status}")
    public Result startOrStop(@PathVariable("status") Integer status, Long id) {
        log.info("起售停售菜品: status={}, id={}", status, id);
        dishService.startOrStop(status, id);
        logicalExpireCache.evictByPrefix(com.sky.controller.user.DishController.DISH_CACHE_PREFIX);
        return Result.success();
    }
}
