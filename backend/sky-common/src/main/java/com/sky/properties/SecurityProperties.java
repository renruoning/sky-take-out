package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.security")
@Data
public class SecurityProperties {

    /**
     * 敏感字段（手机号/身份证号）加密用的口令，实际密钥是对这个口令做SHA-256得到的。
     * 配置里给了一个demo默认值，真实部署必须通过FIELD_ENCRYPTION_KEY环境变量覆盖成只有自己知道的值——
     * 用默认值就等于没加密，任何拿到源码的人都能解出所有数据。
     */
    private String fieldEncryptionKey;

}
