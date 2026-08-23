package com.sky.controller.internal;

import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供sky-order-service通过Feign调用——下单时订单要快照用户姓名（orders.user_name），
 * user表没有跟着订单一起搬，留在sky-server。跟其它internal控制器一样不带鉴权，
 * 靠sky-gateway的InternalPathBlockingFilter挡外部访问。
 */
@RestController
@Slf4j
public class InternalUserController {

    private final UserMapper userMapper;

    InternalUserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/internal/user/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.success(userMapper.getById(id));
    }
}
