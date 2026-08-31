package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.DailyOrderStatDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 取消类状态流转专用：多加一个status IN (expectedStatuses)的CAS条件，返回受影响行数。
     * 0行说明订单当前状态已经不在expectedStatuses里了（被别的路径抢先改掉，或者调用方自己的
     * 前置校验和这次真正执行之间过期了），调用方要按"状态冲突"处理，不能假定这次写一定生效
     * @param orders 只需要设置id和要改的字段（跟update用法一样）
     * @param expectedStatuses 允许执行这次更新的当前状态集合
     */
    int updateWithStatusGuard(@Param("orders") Orders orders, @Param("expectedStatuses") List<Integer> expectedStatuses);

    /**
     * 库存恢复的幂等门闩：CAS更新stock_restored从0到1，返回1表示这次调用者拿到了"去恢复库存"的
     * 资格，返回0表示已经有别的调用把这个订单标记为恢复过了，不该再去调product-service
     * @param id
     */
    int markStockRestored(@Param("id") Long id);

    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 用户历史订单的游标分页查询——按(order_time desc, id desc)排序，不做COUNT(*)，
     * 查limit+1条由调用方判断是否还有下一页（见OrderServiceImpl.pageQuery4User）。
     * cursorOrderTime为null表示查第一页
     * @param userId
     * @param status
     * @param cursorOrderTime
     * @param cursorId
     * @param limit
     */
    List<Orders> pageQueryByCursorForUser(@Param("userId") Long userId,
                                           @Param("status") Integer status,
                                           @Param("cursorOrderTime") LocalDateTime cursorOrderTime,
                                           @Param("cursorId") Long cursorId,
                                           @Param("limit") int limit);

    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量（shopId为null时不限定店铺，供平台超管查看全平台数据）
     * @param status
     * @param shopId
     */
    Integer countStatus(@Param("status") Integer status, @Param("shopId") Long shopId);

    /**
     * 根据状态和下单时间查询订单
     * @param status
     * @param orderTime
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(@Param("status") Integer status, @Param("orderTime") LocalDateTime orderTime);

    /**
     * 根据条件统计营业额（已完成订单的实收金额之和）
     * @param map 包含 status、begin、end
     */
    Double sumByMap(Map<String, Object> map);

    /**
     * 根据条件统计订单数量
     * @param map 包含 status（可选）、begin、end
     */
    Integer countByMap(Map<String, Object> map);

    /**
     * 按天分组统计时间区间内每天的订单总数、有效订单数（validStatus）、营业额，一次查询代替按天循环查询
     * @param begin
     * @param end
     * @param validStatus 视为"有效"的订单状态（已完成）
     */
    List<DailyOrderStatDTO> sumAndCountGroupByDate(@Param("begin") LocalDateTime begin,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("validStatus") Integer validStatus,
                                                    @Param("shopId") Long shopId);
}
