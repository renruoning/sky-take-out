package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShopDTO;
import com.sky.dto.ShopPageQueryDTO;
import com.sky.entity.Shop;
import com.sky.exception.ShopBusinessException;
import com.sky.mapper.ShopMapper;
import com.sky.result.PageResult;
import com.sky.service.ShopService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 店铺（商户）管理 —— 平台超管专用
 */
@Service
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;

    ShopServiceImpl(ShopMapper shopMapper) {
        this.shopMapper = shopMapper;
    }

    /**
     * 只有平台超管（当前请求无店铺上下文）才能管理店铺信息
     */
    private void checkPlatformAdmin() {
        if (BaseContext.getCurrentShopId() != null) {
            throw new ShopBusinessException(MessageConstant.SHOP_PLATFORM_ONLY);
        }
    }

    public void save(ShopDTO shopDTO) {
        checkPlatformAdmin();
        Shop shop = new Shop();
        BeanUtils.copyProperties(shopDTO, shop);
        shop.setStatus(1);
        shopMapper.insert(shop);
    }

    public PageResult pageQuery(ShopPageQueryDTO shopPageQueryDTO) {
        checkPlatformAdmin();
        PageHelper.startPage(shopPageQueryDTO.getPage(), shopPageQueryDTO.getPageSize());
        Page<Shop> page = shopMapper.pageQuery(shopPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public void update(ShopDTO shopDTO) {
        checkPlatformAdmin();
        Shop shop = new Shop();
        BeanUtils.copyProperties(shopDTO, shop);
        shopMapper.update(shop);
    }

    public void startOrStop(Integer status, Long id) {
        checkPlatformAdmin();
        Shop shop = Shop.builder()
                .id(id)
                .status(status)
                .build();
        shopMapper.update(shop);
    }

    public List<Shop> listActive() {
        return shopMapper.listActive();
    }
}
