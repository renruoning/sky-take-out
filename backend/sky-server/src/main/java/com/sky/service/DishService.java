package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;

public interface DishService {
    /**
     * 新增菜品，同时保存口味数据
     * 
     * @param dishDTO 菜品信息
     */
    public void saveWithFlavor(DishDTO dishDTO);

    /**
     * 菜品分页查询
     * 
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);
}
