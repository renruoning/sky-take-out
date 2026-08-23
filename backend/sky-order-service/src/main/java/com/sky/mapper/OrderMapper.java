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
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

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
