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

    /**
     * 批量把商品加入当前用户的购物车（每个商品的名称/图片/金额已经确定，不需要再查一次dish/setmeal表）。
     * 已存在的商品数量累加，不存在的直接新增。目前只有"再来一单"这一处调用。
     * @param items
     */
    void addAll(List<ShoppingCart> items);

    /**
     * 以下三个是显式传userId的版本，供InternalShoppingCartController用——
     * sky-order-service下单/再来一单时需要操作购物车，但它自己的请求上下文里的BaseContext.getCurrentId()
     * 是order-service自己JWT拦截器解析出来的、跟sky-server的BaseContext是两个不同服务里各自的ThreadLocal，
     * 不能跨服务共享，所以内部RPC必须显式传userId，不能依赖BaseContext。
     */
    List<ShoppingCart> showShoppingCart(Long userId);

    void cleanShoppingCart(Long userId);

    void addAll(Long userId, List<ShoppingCart> items);
}
