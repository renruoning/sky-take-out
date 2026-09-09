package com.sky.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在Service/Controller方法上，表示这个方法是可以容忍主从复制延迟的"普通读"，
 * 允许路由到从库。不标这个注解的方法（包括所有写方法、以及需要读到最新数据的"强一致读"，
 * 比如{@code InternalOrderController.getOrderSummary}）都走默认的主库，不需要额外标注——
 * 见{@link com.sky.datasource.DataSourceContextHolder}的说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Slave {
}
