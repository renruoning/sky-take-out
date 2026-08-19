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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

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
        if (shopDTO.getBusinessType() == null) {
            throw new ShopBusinessException(MessageConstant.SHOP_BUSINESS_TYPE_REQUIRED);
        }
        checkSecondaryBusinessTypeDiffers(shopDTO.getBusinessType(), shopDTO.getSecondaryBusinessType());

        Shop shop = new Shop();
        BeanUtils.copyProperties(shopDTO, shop);
        shop.setStatus(1);
        // 注意：这里故意不设置businessTypeUpdatedAt（留空）。如果在建店时就把它设成当前时间，
        // 会导致店铺刚建好、还没真正“改过”一次营业类型，就已经被一年冷却卡住第一次修改——
        // 冷却应该从第一次真实修改开始算，不是从建店开始算。
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

        boolean changingBusinessType = shopDTO.getBusinessType() != null || shopDTO.getSecondaryBusinessType() != null;
        if (changingBusinessType) {
            Shop current = shopMapper.getById(shopDTO.getId());
            Integer newPrimary = shopDTO.getBusinessType() != null ? shopDTO.getBusinessType() : current.getBusinessType();
            Integer newSecondary = shopDTO.getSecondaryBusinessType();
            // 只有真的和数据库里现有值不一样，才算“在改营业类型”，避免把原值原样传回来也触发一年冷却
            boolean primaryChanged = !Objects.equals(newPrimary, current.getBusinessType());
            boolean secondaryChanged = shopDTO.getSecondaryBusinessType() != null
                    && !Objects.equals(newSecondary, current.getSecondaryBusinessType());
            if (primaryChanged || secondaryChanged) {
                if (current.getBusinessTypeUpdatedAt() != null
                        && ChronoUnit.DAYS.between(current.getBusinessTypeUpdatedAt(), LocalDateTime.now()) < 365) {
                    throw new ShopBusinessException(MessageConstant.SHOP_BUSINESS_TYPE_COOLDOWN);
                }
                checkSecondaryBusinessTypeDiffers(newPrimary, newSecondary != null ? newSecondary : current.getSecondaryBusinessType());
                shop.setBusinessTypeUpdatedAt(LocalDateTime.now());
            }
        }

        shopMapper.update(shop);
    }

    /**
     * 副营业类型非空时必须和主营业类型不同
     */
    private void checkSecondaryBusinessTypeDiffers(Integer businessType, Integer secondaryBusinessType) {
        if (secondaryBusinessType != null && secondaryBusinessType.equals(businessType)) {
            throw new ShopBusinessException(MessageConstant.SHOP_BUSINESS_TYPE_DUPLICATE);
        }
    }

    public void startOrStop(Integer status, Long id) {
        checkPlatformAdmin();
        Shop shop = Shop.builder()
                .id(id)
                .status(status)
                .build();
        shopMapper.update(shop);
    }

    public List<Shop> listActive(Integer businessType) {
        return shopMapper.listActive(businessType);
    }
}
