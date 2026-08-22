package com.sky.controller.user;

import com.sky.annotation.RateLimit;
import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserAccountLoginDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.enumeration.RateLimitKeyType;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;


@RestController
@RequestMapping("/user/user")
@Api(tags="C端用户相关接口")
@Slf4j
public class UserController {

    private final UserService userService;
    private final JwtProperties jwtProperties;
    public UserController(UserService userService, JwtProperties jwtProperties) {
        this.userService = userService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 账号密码登录
     * @param userAccountLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("账号密码登录")
    @RateLimit(keyType = RateLimitKeyType.IP, limit = 10, windowSeconds = 60, name = "user_login",
            message = "登录尝试过于频繁，请稍后再试")
    public Result<UserLoginVO> login(@RequestBody UserAccountLoginDTO userAccountLoginDTO){
        log.info("用户登录：{}", userAccountLoginDTO.getUsername());
        User user = userService.accountLogin(userAccountLoginDTO);
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                        .id(user.getId())
                        .openid(user.getOpenid())
                        .token(token)
                        .build();
        return Result.success(userLoginVO);
    }

    /**
     * 微信登录（暂时和微信解绑，先不对外暴露；wxLogin逻辑保留在Service中以便后续恢复）
     * @param userLoginDTO
     * @return
     */
    // @PostMapping("/wxlogin")
    // @ApiOperation("微信登录")
    // public Result<UserLoginVO> wxLogin(@RequestBody UserLoginDTO userLoginDTO){
    //     log.info("微信用户登录：{}",userLoginDTO.getCode());
    //     User user = userService.wxLogin(userLoginDTO);
    //     Map<String, Object> claims = new HashMap<>();
    //     claims.put(JwtClaimsConstant.USER_ID, user.getId());
    //     String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    //     UserLoginVO userLoginVO = UserLoginVO.builder()
    //                     .id(user.getId())
    //                     .openid(user.getOpenid())
    //                     .token(token)
    //                     .build();
    //     return Result.success(userLoginVO);
    // }
}
