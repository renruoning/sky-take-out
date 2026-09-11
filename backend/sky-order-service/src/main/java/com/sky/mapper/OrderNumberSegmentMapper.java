package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderNumberSegmentMapper {

    /**
     * 原子递增max_id：UPDATE本身在这一行上加行锁，并发调用天然串行化，
     * 不需要在应用层自己加锁去抢号段——见database/migration_order_number_segment.sql
     */
    @Update("UPDATE order_number_segment SET max_id = max_id + #{step} WHERE biz_key = #{bizKey}")
    void incrementMaxId(@Param("bizKey") String bizKey, @Param("step") int step);

    @Select("SELECT max_id FROM order_number_segment WHERE biz_key = #{bizKey}")
    Long getMaxId(@Param("bizKey") String bizKey);
}
