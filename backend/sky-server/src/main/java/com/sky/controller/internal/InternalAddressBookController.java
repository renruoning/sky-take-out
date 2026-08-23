package com.sky.controller.internal;

import com.sky.entity.AddressBook;
import com.sky.service.AddressBookService;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供sky-order-service通过Feign调用——下单时要校验/读取收货地址，address_book这次没有跟着订单一起搬，
 * 留在sky-server（用户资料的一部分，员工管理/用户资料这条线都留在这里）。跟其它internal控制器一样，
 * 不带鉴权，靠sky-gateway的InternalPathBlockingFilter挡外部访问。
 */
@RestController
@Slf4j
public class InternalAddressBookController {

    private final AddressBookService addressBookService;

    InternalAddressBookController(AddressBookService addressBookService) {
        this.addressBookService = addressBookService;
    }

    @GetMapping("/internal/address-book/{id}")
    public Result<AddressBook> getById(@PathVariable Long id) {
        return Result.success(addressBookService.getById(id));
    }
}
