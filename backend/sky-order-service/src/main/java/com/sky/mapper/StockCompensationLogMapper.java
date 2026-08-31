package com.sky.mapper;

import com.sky.entity.StockCompensationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 下单失败后的库存补偿记录——见StockCompensationLog注释
 */
@Mapper
public interface StockCompensationLogMapper {

    /**
     * 插入一条待补偿记录。调用方必须用REQUIRES_NEW事务执行这条插入，不能让它跟着submit()
     * 本身的事务一起回滚（见OrderServiceImpl.persistPendingCompensation）
     */
    void insert(StockCompensationLog log);

    /**
     * CAS：只有还是PENDING状态时才能标记为DONE，返回0说明已经被标记过完成。
     * 调用方必须在真正调用product-service恢复库存成功之后才调这个方法——跟
     * OrderMapper.markStockRestored反过来的顺序是刻意的：这张表存在的意义就是兜底重试，
     * 如果标记完成放在调用之前，一旦调用失败就再也没有重试机会了，跟这张表的设计目的矛盾
     */
    int markDone(@Param("id") Long id);

    /**
     * 定时任务专用：扫出所有还没补偿成功的记录，按创建时间升序，限制一次处理的条数，
     * 避免一次扫出太多拖慢一次定时任务的执行
     */
    @Select("select * from stock_compensation_log where status = 0 order by create_time asc limit #{limit}")
    List<StockCompensationLog> getPending(@Param("limit") int limit);
}
