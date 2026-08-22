package com.sky.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感字段（手机号、身份证号）落库前的加解密工具：AES-256-GCM，key由配置的口令做SHA-256得到。
 * 存储格式是 base64(12字节随机IV + 密文+GCM认证标签)，每次加密同一个明文密文都不一样（IV随机），
 * 这意味着这些字段没法再用于SQL里的等值/模糊查询——目前phone/idNumber在这几张表里都不是查询条件，
 * 所以这个代价是可以接受的；如果以后要按手机号查人，需要另外维护一个手机号的HMAC索引字段。
 */
@Slf4j
public class FieldCryptoUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private FieldCryptoUtil() {
    }

    public static String encrypt(String plainText, String passphrase) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherBytes, 0, result, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密失败（最常见的原因：这行数据是加密上线之前写入的旧明文数据，根本不是密文）时，
     * 原样把输入返回而不是抛异常——不然老数据会导致所有查到它的接口直接500，这个代价比"偶尔展示一条未加密的老数据"更糟。
     */
    public static String decrypt(String storedValue, String passphrase) {
        if (storedValue == null || storedValue.isEmpty()) {
            return storedValue;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(storedValue);
            if (raw.length <= GCM_IV_LENGTH) {
                return storedValue;
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherBytes = new byte[raw.length - GCM_IV_LENGTH];
            System.arraycopy(raw, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("敏感字段解密失败，按未加密的旧数据原样返回：{}", e.getMessage());
            return storedValue;
        }
    }

    private static SecretKeySpec deriveKey(String passphrase) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] key = sha256.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}
