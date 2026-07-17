package com.sky.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SetmealDishMapper {

    /**
     * 根据菜品id查询关联的套餐id
     * 
     * @param dishIds 菜品id列表
     * @return 关联的套餐id列表
     */
    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);
}
