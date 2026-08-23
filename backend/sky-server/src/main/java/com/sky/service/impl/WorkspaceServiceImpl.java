package com.sky.service.impl;

import com.sky.client.OrderClient;
import com.sky.client.ProductClient;
import com.sky.context.BaseContext;
import com.sky.mapper.UserMapper;
import com.sky.result.Result;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderBusinessStatVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ProductClient productClient;
    @Autowired
    private OrderClient orderClient;

    /**
     * 根据时间段统计营业数据
     * @param begin
     * @param end
     * @return
     */
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */

        Long shopId = BaseContext.getCurrentShopId();

        // 订单相关的3个原始数字（总订单数/有效订单数/营业额）一次Feign往返拿齐，order/order_detail已经搬去了order-service
        Result<OrderBusinessStatVO> statResult = orderClient.getBusinessStats(shopId, begin, end);
        OrderBusinessStatVO stat = (statResult != null && statResult.getCode() != null && statResult.getCode() == 1)
                ? statResult.getData() : null;
        Integer totalOrderCount = stat != null ? stat.getTotalOrderCount() : 0;
        Integer validOrderCount = stat != null ? stat.getValidOrderCount() : 0;
        Double turnover = stat != null && stat.getTurnover() != null ? stat.getTurnover() : 0.0;

        Double unitPrice = 0.0;
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0 && validOrderCount != 0) {
            //订单完成率
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            //平均客单价
            unitPrice = turnover / validOrderCount;
        }

        //新增用户数：user表没有跟着订单一起搬，本地查询不变
        Map map = new HashMap();
        map.put("shopId", shopId);
        map.put("begin", begin);
        map.put("end", end);
        Integer newUsers = userMapper.countByMap(map);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }


    /**
     * 查询订单管理数据
     *
     * @return
     */
    public OrderOverViewVO getOrderOverView() {
        Result<OrderOverViewVO> result = orderClient.getOrderOverview(BaseContext.getCurrentShopId());
        return (result != null && result.getCode() != null && result.getCode() == 1) ? result.getData() : null;
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    public DishOverViewVO getDishOverView() {
        Result<DishOverViewVO> result = productClient.getDishOverview(BaseContext.getCurrentShopId());
        return (result != null && result.getCode() != null && result.getCode() == 1) ? result.getData() : null;
    }

    /**
     * 查询套餐总览
     *
     * @return
     */
    public SetmealOverViewVO getSetmealOverView() {
        Result<SetmealOverViewVO> result = productClient.getSetmealOverview(BaseContext.getCurrentShopId());
        return (result != null && result.getCode() != null && result.getCode() == 1) ? result.getData() : null;
    }
}
