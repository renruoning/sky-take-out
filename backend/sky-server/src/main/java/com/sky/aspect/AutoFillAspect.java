package com.sky.aspect;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;

import org.aspectj.lang.reflect.MethodSignature;

import lombok.extern.slf4j.Slf4j;

/**
 * 自定义切面，实现公共字段自动填充的逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    // 切入点
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut() {
    }

    /**
     * 前置通知，再通知中进行公共字段的赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始自动填充公共字段");
        // 开始获取当前被拦截的方法的数据库操作类型
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();// 方法签名对象
        AutoFill autofill = methodSignature.getMethod().getAnnotation(AutoFill.class);// 获得方法上的注解对象
        OperationType operationType = autofill.value();// 获得数据库操作类型
        // 获取当前被拦截的方法的参数--实体对象
        Object[] args = joinPoint.getArgs();// 获得方法的参数列表
        // 如果参数列表为空，则不进行任何处理
        if (args == null || args.length == 0) {
            return;
        }
        Object entity = args[0];// 获得第一个参数对象

        // 为公共属性赋值
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        // 根据不同的操作类型，为对应的属性通过反射调用 setter 方法来赋值
        if (operationType == OperationType.INSERT) {
            try {
                // 通过反射获取四个公共字段的 setter 方法
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射调用 setter 为对象赋值
                setCreateTime.invoke(entity, now);
                setUpdateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                log.error("自动填充insert公共字段失败", e);
            }

            // 店铺id单独一套try/catch：只有当前请求带有明确的店铺上下文（非平台超管）才自动填充，
            // 避免覆盖服务层为超管场景手动指定的shopId，也不影响没有shopId字段的实体（如Employee由超管创建时）
            Long currentShopId = BaseContext.getCurrentShopId();
            if (currentShopId != null) {
                try {
                    Method setShopId = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_SHOP_ID, Long.class);
                    setShopId.invoke(entity, currentShopId);
                } catch (Exception e) {
                    // 实体没有shopId字段（如user/address_book相关实体），无需处理
                }
            }
        } else if (operationType == OperationType.UPDATE) {
            try {
                // 通过反射获取两个公共字段的 setter 方法
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                // 通过反射调用 setter 为对象赋值
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentId);
            } catch (Exception e) {
                log.error("自动填充update公共字段失败", e);
            }
        }
    }
}
