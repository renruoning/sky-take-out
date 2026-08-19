package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class ShoppingCartDTO implements Serializable {

    private Long dishId;
    private Long setmealId;
    private String dishFlavor;

    //所属店铺id（客户端选店后传入，加入购物车的商品必须归属该店铺）
    private Long shopId;

}
