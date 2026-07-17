package com.sky.service;

import java.util.List;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

public interface DishService {
    /**
     * 新增菜品，同时保存口味数据
     * 
     * @param dishDTO 菜品信息
     */
    public void saveWithFlavor(DishDTO dishDTO);

    public void deleteBatch(List<Long> ids);

    /**
     * 菜品分页查询
     * 
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据ID查询菜品信息和口味信息
     * 
     * @param dishId 菜品ID
     * @return 菜品信息和口味信息
     */
    public DishVO getByIdWithFlavor(Long dishId);

    /**
     * 根据ID修改菜品信息和口味信息
     * 
     * @param dishDTO 菜品信息
     */
    public void updateWithFlavor(DishDTO dishDTO);
}
