package com.sky.saga;

import com.sky.client.ProductClient;
import com.sky.client.SkyServerClient;
import com.sky.config.RabbitMQConfig;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.StockChangeItemDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.StockBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.ordernumber.OrderNumberGenerator;
import com.sky.result.Result;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * submitOrderSaga状态机（见resources/statelang/submit_order.json）绑定的两个正向动作+一个补偿动作：
 * DeductStock/CompensateDeductStock/CreateOrder。取代原来OrderServiceImpl里手写的
 * restoreStockWithRetry+StockCompensationLog+StockCompensationTask那一套——建单失败后的
 * 库存补偿重试改由Seata TC的全局事务回滚重试机制兜底，不用自己再维护一张补偿记录表和定时任务。
 * <p>
 * Bean名必须是"submitOrderSagaActions"，跟JSON状态机定义里的ServiceName对应
 */
@Component("submitOrderSagaActions")
@Slf4j
public class SubmitOrderSagaActions {

    private final ProductClient productClient;
    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final SkyServerClient skyServerClient;
    private final RabbitTemplate rabbitTemplate;
    private final OrderNumberGenerator orderNumberGenerator;

    SubmitOrderSagaActions(ProductClient productClient, OrderMapper orderMapper, OrderDetailMapper orderDetailMapper,
                            SkyServerClient skyServerClient, RabbitTemplate rabbitTemplate,
                            OrderNumberGenerator orderNumberGenerator) {
        this.productClient = productClient;
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.skyServerClient = skyServerClient;
        this.rabbitTemplate = rabbitTemplate;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    private static <T> T unwrap(Result<T> result) {
        return (result != null && result.getCode() != null && result.getCode() == 1) ? result.getData() : null;
    }

    /**
     * DeductStock状态：正向扣库存。两种失败区分了异常类型，对应submit_order.json里的Status配置：
     * - Feign调用本身失败（网络超时/商品服务不可达）：真正"不确定"——超时有可能是请求其实已经在
     *   对方那边处理完了、只是响应没送回来，所以保持引擎默认的UN状态，让CompensateDeductStock
     *   照常执行一次restoreStock兜底（restoreStock本身没有条件判断，多调一次不会把库存越加越多，
     *   因为deductStock要么整批全扣成功要么全不扣，见StockServiceImpl.deductStock的注释）
     * - 商品服务明确返回"库存不足"：这是product-service用一条"stock >= number"的条件UPDATE判断出来的，
     *   受影响行数为0直接抛异常回滚，能确定这次调用完全没有改动任何数据——用StockBusinessException
     *   （区别于上面的OrderBusinessException）让状态机的Status规则把这个状态判成FA（确定失败，
     *   不需要补偿）。如果不做这个区分，笼统按UN处理会导致CompensateDeductStock在库存根本没被扣过
     *   的情况下还去调一次restoreStock，凭空把库存加多了——这是实测复现过的真实bug，不是纸面推演
     */
    public void deductStock(List<StockChangeItemDTO> stockItems) {
        Result<String> deductResult;
        try {
            deductResult = productClient.deductStock(stockItems);
        } catch (Exception e) {
            log.error("扣减库存的Feign调用失败（商品服务不可达），本次下单直接失败，不建立订单", e);
            throw new OrderBusinessException(MessageConstant.STOCK_SERVICE_UNAVAILABLE);
        }
        if (deductResult == null || deductResult.getCode() == null || deductResult.getCode() != 1) {
            String msg = (deductResult != null && deductResult.getMsg() != null)
                    ? deductResult.getMsg() : MessageConstant.STOCK_NOT_ENOUGH;
            throw new StockBusinessException(msg);
        }
    }

    /**
     * CompensateDeductStock：DeductStock的补偿动作，只在CreateOrder失败、引擎触发回滚时被调用。
     * 调用失败（product-service不可达）会让这个全局事务的补偿状态变成重试中，由Seata TC按它自己的
     * 全局事务回滚重试策略持续重试直到成功，不需要在这里自己再写一层重试
     */
    public void compensateDeductStock(List<StockChangeItemDTO> stockItems) {
        productClient.restoreStock(stockItems);
    }

    /**
     * CreateOrder状态：建订单、插订单明细、清购物车、发超时取消延迟消息，逻辑原样从
     * OrderServiceImpl.submit()搬过来，本地仍然是一个独立的@Transactional。
     * <p>
     * 两点跟直接搬过来不一样，都是踩出来的坑：
     * 1) addressBook/购物车/user没有直接复用submit()里已经查过一次的结果，而是在这里重新查——
     *    实测发现Seata Saga的Input传自定义的多层嵌套包装对象（试过一版SubmitOrderContext，
     *    内层套AddressBook/List&lt;ShoppingCart&gt;）过一遍引擎的参数绑定后字段会丢（实测复现：
     *    嵌套字段变成null，BeanUtils.copyProperties直接抛IllegalArgumentException）
     * 2) OrdersSubmitDTO也没有整个对象传进来，而是拆成一个个标量参数——同样是实测踩出来的：
     *    整个DTO传进来时packAmount/amount这类Integer/BigDecimal字段过一遍引擎的重建后类型对不上，
     *    BeanUtils.copyProperties会抛"Could not copy property"。拆成标量参数后，引擎按方法签名
     *    上明确的参数类型转换，不需要凭空猜一个自定义类的字段类型，这条路径实测是可靠的
     * 多付出的两次Feign调用（地址簿、购物车）换来的是不用再赌这两层参数绑定的可靠性
     */
    @Transactional
    public OrderSubmitVO createOrder(Long userId, Long addressBookId, Integer payMethod, String remark,
                                      LocalDateTime estimatedDeliveryTime, Integer deliveryStatus,
                                      Integer tablewareNumber, Integer tablewareStatus, Integer packAmount,
                                      BigDecimal amount) {
        OrdersSubmitDTO ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(addressBookId);
        ordersSubmitDTO.setPayMethod(payMethod == null ? 0 : payMethod);
        ordersSubmitDTO.setRemark(remark);
        ordersSubmitDTO.setEstimatedDeliveryTime(estimatedDeliveryTime);
        ordersSubmitDTO.setDeliveryStatus(deliveryStatus);
        ordersSubmitDTO.setTablewareNumber(tablewareNumber);
        ordersSubmitDTO.setTablewareStatus(tablewareStatus);
        ordersSubmitDTO.setPackAmount(packAmount);
        ordersSubmitDTO.setAmount(amount);

        AddressBook addressBook = unwrap(skyServerClient.getAddressBook(addressBookId));
        List<ShoppingCart> shoppingCartList = unwrap(skyServerClient.getCart(userId));
        User user = unwrap(skyServerClient.getUser(userId));

        // 插入订单：店铺id取自购物车（购物车已保证单一店铺，不信任客户端传入的店铺id）
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setNumber(orderNumberGenerator.nextOrderNumber());
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setUserId(userId);
        orders.setShopId(shoppingCartList.get(0).getShopId());
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserName(user.getName());
        orders.setUserAvatar(user.getAvatar());
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName()
                + addressBook.getDistrictName() + addressBook.getDetail());

        orderMapper.insert(orders);

        // 插入订单明细
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 清空购物车
        skyServerClient.cleanCart(userId);

        // 发送延迟消息，若30分钟后订单仍未支付则自动取消。
        // RabbitMQ不可达不该导致下单直接失败——那样"能不能点餐"就被一个订单超时兜底机制卡住了，
        // 代价是这一笔订单万一真没付款也不会被自动取消，需要靠人工/对账兜底，两害相权取其轻。
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_TIMEOUT_DELAY_ROUTING_KEY, orders.getId());
        } catch (Exception e) {
            log.error("订单{}的超时取消延迟消息发送失败（RabbitMQ不可达），订单已正常创建，但不会被自动取消，需要人工关注", orders.getId(), e);
        }

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }
}
