package com.sky.controller.internal;

import com.sky.annotation.Slave;
import com.sky.constant.MessageConstant;
import com.sky.dto.DailyOrderStatDTO;
import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.entity.Shop;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShopMapper;
import com.sky.result.Result;
import com.sky.vo.OrderBusinessStatVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供其它微服务通过Feign调用的内部接口，不是给终端用户/前端用的——路径不在/admin或/user下，
 * 不会被现有的JWT拦截器拦截，鉴权靠"这个路径只允许服务间直连访问、Gateway层面挡掉外部访问"来保证
 * （见sky-gateway的InternalPathBlockingFilter），不是靠用户/员工token。
 * <p>
 * shop表这次跟订单一起搬了过来，getOrderSummary()里查shopName变成同库本地join，不用再调一次Feign。
 * userName/userAvatar这两个字段user表没搬，改成调用方（sky-server的InvoiceServiceImpl/ReviewServiceImpl）
 * 自己在本地已经有的user表数据里拼，这次改成order-service这边不再查user表——见下方getOrderSummary()注释。
 */
@RestController
@Slf4j
public class InternalOrderController {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final ShopMapper shopMapper;

    InternalOrderController(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, ShopMapper shopMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.shopMapper = shopMapper;
    }

    /**
     * 故意不标@Slave：这是"强一致读"的代表场景——invoice-service/review-service申请发票/提交评价前
     * 会调这个接口校验订单归属和状态（比如刚confirm完的订单status是不是已经推进到COMPLETED），
     * 如果读到从库上还没同步过来的旧状态，会出现"订单其实已完成，但发票服务查到的还是待完成"这种
     * 错误拒绝。读写分离里"写走主库、普通读走从库、强一致读走主库"这三类里的最后一类，不需要
     * 额外代码——默认（不标注）就是走主库，见DataSourceContextHolder的说明
     */
    @GetMapping("/internal/order/{id}")
    public Result<OrderSummaryVO> getOrderSummary(@PathVariable Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            return Result.error(MessageConstant.ORDER_NOT_FOUND);
        }
        Shop shop = shopMapper.getById(order.getShopId());
        return Result.success(OrderSummaryVO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .shopId(order.getShopId())
                .number(order.getNumber())
                .status(order.getStatus())
                .payStatus(order.getPayStatus())
                .amount(order.getAmount())
                .orderTime(order.getOrderTime())
                .shopName(shop != null ? shop.getName() : null)
                // orders表本身已经在下单时快照了userName/userAvatar（见OrderServiceImpl.submit()），
                // 不需要再查一次user表——user服务没有跟着订单一起搬，这样也少一次跨服务查询
                .userName(order.getUserName())
                .userAvatar(order.getUserAvatar())
                .build());
    }

    /**
     * 工作台"营业数据"用的原始订单统计数字，收敛了原来WorkspaceServiceImpl.getBusinessData()里
     * 对同一个map对象反复变更status字段发起的3次countByMap/sumByMap调用，一次Feign往返返回。
     * 标了@Slave：报表聚合类查询天然容忍统计数字比主库晚几十毫秒
     */
    @Slave
    @GetMapping("/internal/order/business-stats")
    public Result<OrderBusinessStatVO> getBusinessStats(@RequestParam Long shopId,
                                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime begin,
                                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("begin", begin);
        map.put("end", end);

        Integer totalOrderCount = orderMapper.countByMap(map);

        map.put("status", Orders.COMPLETED);
        Double turnover = orderMapper.sumByMap(map);
        turnover = turnover == null ? 0.0 : turnover;
        Integer validOrderCount = orderMapper.countByMap(map);

        return Result.success(OrderBusinessStatVO.builder()
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .turnover(turnover)
                .build());
    }

    /**
     * 管理端工作台"订单管理"总览，收敛了原来WorkspaceServiceImpl.getOrderOverView()里
     * 对5种状态分别发起的5次countByMap调用，一次Feign往返返回。标了@Slave，理由同上
     */
    @Slave
    @GetMapping("/internal/order/overview")
    public Result<OrderOverViewVO> getOrderOverview(@RequestParam Long shopId) {
        Map<String, Object> map = new HashMap<>();
        map.put("shopId", shopId);
        map.put("begin", LocalDateTime.now().with(LocalTime.MIN));

        map.put("status", Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = orderMapper.countByMap(map);

        map.put("status", Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.countByMap(map);

        map.put("status", Orders.COMPLETED);
        Integer completedOrders = orderMapper.countByMap(map);

        map.put("status", Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.countByMap(map);

        map.put("status", null);
        Integer allOrders = orderMapper.countByMap(map);

        return Result.success(OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build());
    }

    /**
     * 报表用的按天分组订单统计，供ReportServiceImpl的turnoverStatistics/ordersStatistics/
     * exportBusinessData共用一次查询结果（原来的getDailyOrderStatMap()逻辑原样搬过来）。
     * 标了@Slave，理由同上
     */
    @Slave
    @GetMapping("/internal/order/daily-stats")
    public Result<List<DailyOrderStatDTO>> getDailyStats(@RequestParam Long shopId,
                                                           @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate begin,
                                                           @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate end) {
        return Result.success(orderMapper.sumAndCountGroupByDate(
                LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, java.time.LocalTime.MAX), Orders.COMPLETED, shopId));
    }

    /**
     * 报表用的销量排名top10（原来ReportServiceImpl.salesTop10Statistics()里直接查orderDetailMapper那部分）。
     * 标了@Slave，理由同上
     */
    @Slave
    @GetMapping("/internal/order/sales-top10")
    public Result<List<GoodsSalesDTO>> getSalesTop10(@RequestParam Long shopId,
                                                       @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate begin,
                                                       @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate end) {
        return Result.success(orderDetailMapper.getSalesTop10(
                Orders.COMPLETED, LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, java.time.LocalTime.MAX), shopId));
    }
}
