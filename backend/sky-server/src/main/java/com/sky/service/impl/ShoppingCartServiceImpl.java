package com.sky.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ShoppingCartMapper shoppingCartMapper;
    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    ShoppingCartServiceImpl(ShoppingCartMapper shoppingCartMapper, DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 判断该商品（同一菜品/套餐+同一口味）是否已经在购物车中
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && !list.isEmpty()) {
            // 已存在，数量加一
            ShoppingCart existCart = list.get(0);
            existCart.setNumber(existCart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(existCart);
            return;
        }

        // 不存在，需要根据是菜品还是套餐查询详情，填充名称、图片、金额后插入一条新记录
        Long dishId = shoppingCartDTO.getDishId();
        if (dishId != null) {
            Dish dish = dishMapper.getById(dishId);
            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
        } else {
            Long setmealId = shoppingCartDTO.getSetmealId();
            Setmeal setmeal = setmealMapper.getById(setmealId);
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        }
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());
        shoppingCartMapper.insert(shoppingCart);
    }

    /**
     * 查看当前用户的购物车
     * @return
     */
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 删除/减少购物车中的一个商品
     * @param shoppingCartDTO
     */
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 定位到具体是购物车中的哪一行
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.isEmpty()) {
            return;
        }

        ShoppingCart existCart = list.get(0);
        if (existCart.getNumber() == 1) {
            // 数量为1，直接删除这一行
            shoppingCartMapper.deleteById(existCart.getId());
        } else {
            // 数量减一
            existCart.setNumber(existCart.getNumber() - 1);
            shoppingCartMapper.updateNumberById(existCart);
        }
    }

    /**
     * 清空当前用户的购物车
     */
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }
}
