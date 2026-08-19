package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户账号密码登录
 */
@Data
public class UserAccountLoginDTO implements Serializable {

    private String username;

    private String password;

}
