package com.sky.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserAccountLoginDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.LoginFailedException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    //微信服务接口地址
    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private WeChatProperties wechatProperties;
    private final UserMapper userMapper;
    public UserServiceImpl(WeChatProperties wechatProperties, UserMapper userMapper) {
        this.wechatProperties = wechatProperties;
        this.userMapper = userMapper;
    }
    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    public User wxLogin(UserLoginDTO userLoginDTO) {
        // 调用微信的接口获取用户信息，并保存到数据库中
        String openid = getOpenid(userLoginDTO.getCode());
        
        //判断openid是否为空,如果为空说明登录失败，抛出登录失败异常
        if (openid == null) {
            log.error("微信登录失败");
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //判断这个用户对于苍穹外卖是否是新用户
        User user = userMapper.getByOpenid(openid);

        //如果是新用户自动完成注册并保存到数据库
        if (user == null) {
            user = User.builder()
                .openid(openid)
                .createTime(LocalDateTime.now())
                .build();
            userMapper.insert(user);
        }
        return user;
    }

    /**
     * 账号密码登录
     * @param userAccountLoginDTO
     * @return
     */
    public User accountLogin(UserAccountLoginDTO userAccountLoginDTO) {
        String username = userAccountLoginDTO.getUsername();
        String password = userAccountLoginDTO.getPassword();

        User user = userMapper.getByUsername(username);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        return user;
    }

    /**
     * 调用微信的接口获取用户信息，并保存到数据库中
     * @param code
     * @return String openid
     */
    private String getOpenid(String code) {
        // 调用微信的接口获取用户信息，并保存到数据库中
        Map<String, String> params = new HashMap<>();
        params.put("appid", wechatProperties.getAppid());
        params.put("secret", wechatProperties.getSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN_URL, params);

        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");

        return openid;
    }
}
