package com.sky.service;

import com.sky.dto.ShopDTO;
import com.sky.dto.ShopPageQueryDTO;
import com.sky.entity.Shop;
import com.sky.result.PageResult;

import java.util.List;

public interface ShopService {

    /**
     * 新增店铺（平台超管专用）
     * @param shopDTO
     */
    void save(ShopDTO shopDTO);

    /**
     * 分页查询店铺（平台超管专用）
     * @param shopPageQueryDTO
     * @return
     */
    PageResult pageQuery(ShopPageQueryDTO shopPageQueryDTO);

    /**
     * 修改店铺信息（平台超管专用）
     * @param shopDTO
     */
    void update(ShopDTO shopDTO);

    /**
     * 启用、禁用店铺（平台超管专用）
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 查询所有启用中的店铺（用户端选店铺）
     * @param businessType 按主/副营业类型过滤，null表示不过滤
     * @return
     */
    List<Shop> listActive(Integer businessType);
}
