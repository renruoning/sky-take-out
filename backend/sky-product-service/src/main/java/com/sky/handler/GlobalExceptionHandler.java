package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，跟sky-server的同名类完全一样，复制过来是因为它是几十行的自包含小类，
 * 没有必要为了它把sky-web层的公共部分抽到sky-common
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler
    public Result<?> exceptionHandler(BaseException ex) {
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler
    public Result<?> exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        String message = ex.getMessage();
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String msg = split[2] + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    @ExceptionHandler
    public Result<?> exceptionHandler(Exception ex) {
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }
}
