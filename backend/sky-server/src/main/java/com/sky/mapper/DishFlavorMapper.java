package com.sky.mapper;

import java.util.List;

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
}
