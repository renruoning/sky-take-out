package com.sky.service;

import java.util.List;

import com.sky.dto.DishDTO;

public interface DishService {
    public void saveWithFlavor(DishDTO dishDTO);

    public void deleteBatch(List<Long> ids);
}
