package com.sky.mapper;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderDetailMapper {

    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 查询指定时间区间内销量排名前10的商品
     * @param status 订单状态（已完成）
     * @param begin
     * @param end
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("status") Integer status,
                                       @Param("begin") LocalDateTime begin,
                                       @Param("end") LocalDateTime end,
                                       @Param("shopId") Long shopId);
}
