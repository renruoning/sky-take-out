package com.sky.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import com.sky.entity.DishFlavor;

@Mapper
public interface DishFlavorMapper {
    /**
     * 批量插入口味信息
     * 
     * @param flavors 口味信息列表
     */
    void insertBatch(List<DishFlavor> flavors);

    /**
     * 根据菜品id删除口味信息
     * 
     * @param dishId 菜品id
     */
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);
}
