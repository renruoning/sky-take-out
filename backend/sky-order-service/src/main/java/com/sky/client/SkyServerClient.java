package com.sky.client;

import com.sky.entity.AddressBook;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 通过Nacos服务发现直连sky-server的内部接口（不走Gateway）。
 * address_book/user/购物车(Redis)都没有跟着订单一起搬，留在sky-server——下单(submit)/再来一单(repetition)
 * 需要读地址、拿用户名快照、读写购物车，这几个操作原来在同一个进程内直接调用对应Service，
 * 现在必须显式传userId做参数，不能像sky-server自己内部那样依赖BaseContext（见InternalShoppingCartController注释）。
 */
@FeignClient(name = "sky-server")
public interface SkyServerClient {

    @GetMapping("/internal/address-book/{id}")
    Result<AddressBook> getAddressBook(@PathVariable("id") Long id);

    @GetMapping("/internal/user/{id}")
    Result<User> getUser(@PathVariable("id") Long id);

    @GetMapping("/internal/cart")
    Result<List<ShoppingCart>> getCart(@RequestParam("userId") Long userId);

    @PostMapping("/internal/cart/clean")
    Result<String> cleanCart(@RequestParam("userId") Long userId);

    @PostMapping("/internal/cart/add-all")
    Result<String> addAllToCart(@RequestParam("userId") Long userId, @RequestBody List<ShoppingCart> items);
}
