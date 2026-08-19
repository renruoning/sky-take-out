package com.sky.service;

import com.sky.dto.UserAccountLoginDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;

public interface UserService {
    /**
     * 微信登录（暂时不用，保留以便后续恢复）
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 账号密码登录
     * @param userAccountLoginDTO
     * @return
     */
    User accountLogin(UserAccountLoginDTO userAccountLoginDTO);
}
