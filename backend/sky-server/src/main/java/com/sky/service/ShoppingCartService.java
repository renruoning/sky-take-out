package com.sky.service;

import java.util.List;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看当前用户的购物车
     * @return
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 删除/减少购物车中的一个商品
     * @param shoppingCartDTO
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空当前用户的购物车
     */
    void cleanShoppingCart();
}
