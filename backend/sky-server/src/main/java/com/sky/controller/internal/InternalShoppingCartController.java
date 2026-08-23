package com.sky.controller.internal;

import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供sky-order-service通过Feign调用——购物车存Redis，没有跟着订单一起搬（购物车是"正在下的这一单"
 * 之前的暂存状态，本质更接近用户端的临时数据，且已经依赖ProductClient查商品，留在sky-server
 * 离商品/用户这条线更近）。下单(submit)要读+清空购物车，"再来一单"(repetition)要批量写入购物车，
 * 这三个操作都显式传userId，不能像sky-server自己的/user/shoppingCart/**接口那样靠BaseContext——
 * BaseContext是各服务自己请求上下文里的ThreadLocal，不能跨服务共享。
 * 跟其它internal控制器一样不带鉴权，靠sky-gateway的InternalPathBlockingFilter挡外部访问。
 */
@RestController
@Slf4j
public class InternalShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    InternalShoppingCartController(ShoppingCartService shoppingCartService) {
        this.shoppingCartService = shoppingCartService;
    }

    @GetMapping("/internal/cart")
    public Result<List<ShoppingCart>> show(@RequestParam Long userId) {
        return Result.success(shoppingCartService.showShoppingCart(userId));
    }

    @PostMapping("/internal/cart/clean")
    public Result<String> clean(@RequestParam Long userId) {
        shoppingCartService.cleanShoppingCart(userId);
        return Result.success();
    }

    @PostMapping("/internal/cart/add-all")
    public Result<String> addAll(@RequestParam Long userId, @RequestBody List<ShoppingCart> items) {
        shoppingCartService.addAll(userId, items);
        return Result.success();
    }
}
