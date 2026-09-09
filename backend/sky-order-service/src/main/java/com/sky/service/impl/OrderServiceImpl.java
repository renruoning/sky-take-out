package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.client.ProductClient;
import com.sky.client.SkyServerClient;
import com.sky.dto.StockChangeItemDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.exception.StockBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.result.Result;
import com.sky.result.PageResult;
import com.sky.result.CursorPageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.config.RabbitMQConfig;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateMachineInstance;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final SkyServerClient skyServerClient;
    private final ProductClient productClient;
    private final WeChatPayUtil weChatPayUtil;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final StateMachineEngine stateMachineEngine;

    private static final String SUBMIT_LOCK_PREFIX = "lock:order:submit:";
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofSeconds(5);

    // 取消订单的分布式锁：用户手动取消（userCancelById）、管理端取消（cancel）、支付超时自动取消
    // （OrderTimeoutListener.handleOrderTimeout）三条路径都是"读一次状态、判断能不能取消、再写"，
    // 互相之间完全没有互斥——真赶上两条路径同时打同一个订单（最常见的是用户手动取消和超时自动取消撞车），
    // 两边都读到"还能取消"的旧状态，都各自发一条UPDATE，取消原因/时间会被后写的那次悄悄覆盖掉，
    // 属于同一个订单状态但被安上了错误的取消原因，这是真实存在但目前还没暴露出来的数据正确性问题
    // （P4以后order-service真扩多实例才会让这个窗口变得更容易撞上）。P0的SETNX只解决了单实例下
    // "同一用户重复点提交"这个场景，跟这里要挡的竞态不是一回事，所以另开一把锁，不复用那把。
    static final String CANCEL_LOCK_PREFIX = "lock:order:cancel:";
    private static final long CANCEL_LOCK_WAIT_SECONDS = 2;

    // updateWithStatusGuard的CAS兜底用：每条取消类路径允许执行的"当前状态"集合，跟各自原来
    // 那段if判断的条件一一对应，只是把判断结果也交给数据库在UPDATE的WHERE里再确认一遍
    private static final List<Integer> CANCELLABLE_BY_USER = List.of(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED);
    private static final List<Integer> REJECTABLE = List.of(Orders.TO_BE_CONFIRMED);
    private static final List<Integer> CANCELLABLE_BY_SHOP = List.of(
            Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED, Orders.CONFIRMED,
            Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED);

    OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, SkyServerClient skyServerClient,
                      ProductClient productClient, WeChatPayUtil weChatPayUtil, RabbitTemplate rabbitTemplate,
                      RedisTemplate<String, Object> redisTemplate, RedissonClient redissonClient,
                      StateMachineEngine stateMachineEngine) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.skyServerClient = skyServerClient;
        this.productClient = productClient;
        this.weChatPayUtil = weChatPayUtil;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.stateMachineEngine = stateMachineEngine;
    }

    /**
     * 把购物车/订单明细里的条目按dishId或setmealId合并数量——同一个菜品不同口味在购物车里是
     * 多行，但库存是按菜品维度记的，扣库存要按菜品合并后的总数量扣，不能按购物车行数逐行扣
     */
    private static <T> List<StockChangeItemDTO> mergeStockItems(List<T> source,
            Function<T, Long> dishIdGetter, Function<T, Long> setmealIdGetter, Function<T, Integer> numberGetter) {
        Map<String, StockChangeItemDTO> merged = new LinkedHashMap<>();
        for (T row : source) {
            Long dishId = dishIdGetter.apply(row);
            Long setmealId = setmealIdGetter.apply(row);
            String key = dishId != null ? "D" + dishId : "S" + setmealId;
            StockChangeItemDTO item = merged.get(key);
            if (item == null) {
                merged.put(key, StockChangeItemDTO.builder()
                        .dishId(dishId).setmealId(setmealId).number(numberGetter.apply(row)).build());
            } else {
                item.setNumber(item.getNumber() + numberGetter.apply(row));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 订单取消时的库存补偿：查这笔订单实际买了什么，加回对应的库存。fail-open——恢复失败只记日志，
     * 不能因为一个补偿动作失败就连"取消订单"这个主操作本身都做不成；代价是这种情况下会残留一个
     * 库存和实际订单状态不一致的窗口，需要人工/对账兜底，这是手写补偿事务（而不是Seata这类真正的
     * 分布式事务框架）天然留下的缺口。
     * 调用product-service之前先拿markStockRestored这把幂等门闩——这是防止分布式锁失效时同一笔
     * 订单被重复恢复库存的最后一道保险（锁保护的是这个方法会不会被并发进入，门闩保护的是"就算真的
     * 被并发进入了，也只有一次真正调下去"）。门闩必须在调用product-service之前拿到，不能反过来
     * 先调用再标记：那样就变成"标记了但没调成功"和"调成功了但没标记"两种半途状态都可能出现，
     * 门闩本身就失去意义了
     */
    private void restoreStockForOrder(Long orderId) {
        if (orderMapper.markStockRestored(orderId) == 0) {
            log.info("订单{}库存已经被恢复过（重复触发的取消路径），跳过本次恢复", orderId);
            return;
        }
        try {
            List<OrderDetail> details = orderDetailMapper.getByOrderId(orderId);
            List<StockChangeItemDTO> items = mergeStockItems(details,
                    OrderDetail::getDishId, OrderDetail::getSetmealId, OrderDetail::getNumber);
            if (!items.isEmpty()) {
                productClient.restoreStock(items);
            }
        } catch (Exception e) {
            log.error("订单{}取消后恢复库存失败（商品服务不可达/异常），库存和订单状态出现不一致，需要人工核对！", orderId, e);
        }
    }

    /**
     * CAS(updateWithStatusGuard)没命中之后的复核：只有当前状态已经是CANCELLED，才说明是被另一条
     * 取消类路径（用户/管理端/拒单/超时自动取消）抢先做掉了同一个意图——调用方想要的结果（这笔订单
     * 不再继续）已经达成，应该当成功处理，不抛异常；如果当前状态是别的值（比如已经被接单/派送/完成），
     * 才是真正的状态冲突，需要让调用方知道。没有这一步的话，Redis不可达导致锁失效、两条路径并发撞上
     * 同一个订单时，输掉CAS的那一方会让一次"本该成功"的取消请求对外报错，即便最终结果是对的
     */
    private void verifyAlreadyCancelledOrThrow(Long orderId) {
        Orders latest = orderMapper.getById(orderId);
        if (latest == null || !Orders.CANCELLED.equals(latest.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 取消订单场景专用：抢到锁执行action并保证释放；抢不到锁说明这个订单同一时刻正被另一条取消路径处理，
     * 直接拒绝而不是让两边都以为自己能改，这是HTTP路径（用户/管理端手动取消）用的版本，
     * 抢不到会抛异常让调用方看到明确的"正在处理中"提示。Redisson本身不可达时fail-open：
     * 记日志、跳过加锁直接执行，不能因为一个辅助组件挂了就连取消订单这个基础功能都用不了。
     */
    private void runWithCancelLock(Long orderId, Runnable action) {
        RLock lock;
        boolean acquired;
        try {
            lock = redissonClient.getLock(CANCEL_LOCK_PREFIX + orderId);
            acquired = lock.tryLock(CANCEL_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("获取订单{}取消锁失败，Redisson不可达，本次放弃加锁直接执行（fail-open）", orderId, e);
            action.run();
            return;
        }
        if (!acquired) {
            throw new OrderBusinessException(MessageConstant.ORDER_PROCESSING_CONFLICT);
        }
        try {
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Feign调用失败/返回非成功code时统一处理：返回null，调用方按"查不到"处理（延续拆分前直接查库拿不到就是null的行为）
     */
    private static <T> T unwrap(Result<T> result) {
        return (result != null && result.getCode() != null && result.getCode() == 1) ? result.getData() : null;
    }

    /**
     * 用户下单。不再是@Transactional——真正的两步写入（扣库存/建订单）现在交给Seata Saga状态机
     * 编排，各自的本地事务边界在SubmitOrderSagaActions里，这里只做只读校验+启动状态机，
     * 外层包一个Spring本地事务反而会跟Saga引擎自己的状态日志持久化产生不必要的耦合
     * @param ordersSubmitDTO
     * @return
     */
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        // 防止网络抖动/手滑重复点提交在同一时刻并发下单：用Redis SETNX抢一把按用户维度的短时锁，
        // 抢不到说明上一次提交还没处理完，直接拒绝而不是让两个请求都读到同一份购物车、各自建一笔订单。
        // Redis本身不可达时不能连累下单这条主链路：记日志、放弃这次幂等保护、照常下单（fail-open），
        // 好过让整个点餐功能因为一个辅助组件挂了而全部不可用。
        String lockKey = SUBMIT_LOCK_PREFIX + userId;
        boolean lockAcquired = false;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", SUBMIT_LOCK_TTL);
            if (acquired == null || !acquired) {
                throw new OrderBusinessException(MessageConstant.ORDER_SUBMIT_DUPLICATE);
            }
            lockAcquired = true;
        } catch (OrderBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下单幂等锁访问Redis失败，本次放弃幂等保护直接下单（fail-open）", e);
        }

        try {
            // 校验地址簿是否存在（address_book没有跟着订单一起搬，问sky-server要）
            AddressBook addressBook = unwrap(skyServerClient.getAddressBook(ordersSubmitDTO.getAddressBookId()));
            if (addressBook == null) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }

            // 校验购物车是否为空（购物车存Redis，也没有跟着订单一起搬）
            List<ShoppingCart> shoppingCartList = unwrap(skyServerClient.getCart(userId));
            if (shoppingCartList == null || shoppingCartList.isEmpty()) {
                throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
            }

            // 扣减库存：这个项目里唯一一处"一次业务操作要跨两个服务各自的数据库做写操作"的地方——
            // 库存扣在product-service的库，订单建在order-service自己的库，两边没法用同一个本地事务
            // 盖住。用Seata Saga状态机（resources/statelang/submit_order.json）编排"扣库存->建订单"
            // 这两步：建单失败时引擎自动调用DeductStock声明的CompensateState把库存加回去，补偿失败
            // 由Seata TC自己的全局事务回滚重试机制持续重试，不用再自己维护一张补偿记录表和定时任务
            // （见已删除的StockCompensationLog/StockCompensationTask）
            List<StockChangeItemDTO> stockItems = mergeStockItems(shoppingCartList,
                    ShoppingCart::getDishId, ShoppingCart::getSetmealId, ShoppingCart::getNumber);

            // CreateOrder状态自己会重新查一遍地址簿/购物车（见SubmitOrderSagaActions.createOrder的注释），
            // ordersSubmitDTO也拆成标量字段传，不整个对象传——两者都是Seata Saga参数绑定实测踩出来的坑，
            // 详见SubmitOrderSagaActions.createOrder上面的注释
            Map<String, Object> startParams = new HashMap<>();
            startParams.put("stockItems", stockItems);
            startParams.put("userId", userId);
            startParams.put("addressBookId", ordersSubmitDTO.getAddressBookId());
            startParams.put("payMethod", ordersSubmitDTO.getPayMethod());
            startParams.put("remark", ordersSubmitDTO.getRemark());
            startParams.put("estimatedDeliveryTime", ordersSubmitDTO.getEstimatedDeliveryTime());
            startParams.put("deliveryStatus", ordersSubmitDTO.getDeliveryStatus());
            startParams.put("tablewareNumber", ordersSubmitDTO.getTablewareNumber());
            startParams.put("tablewareStatus", ordersSubmitDTO.getTablewareStatus());
            startParams.put("packAmount", ordersSubmitDTO.getPackAmount());
            startParams.put("amount", ordersSubmitDTO.getAmount());

            StateMachineInstance instance = stateMachineEngine.startWithBusinessKey(
                    "submitOrderSaga", null, UUID.randomUUID().toString(), startParams);

            if (instance.getStatus() != ExecutionStatus.SU) {
                Exception ex = instance.getException();
                log.error("提交订单的Saga状态机执行失败，status={}，stockItems={}", instance.getStatus(), stockItems, ex);
                if (ex instanceof OrderBusinessException) {
                    throw (OrderBusinessException) ex;
                }
                if (ex instanceof StockBusinessException) {
                    throw new OrderBusinessException(ex.getMessage());
                }
                throw new OrderBusinessException(MessageConstant.STOCK_NOT_ENOUGH);
            }
            return (OrderSubmitVO) instance.getEndParams().get("orderSubmitResult");
        } finally {
            if (lockAcquired) {
                try {
                    redisTemplate.delete(lockKey);
                } catch (Exception e) {
                    log.error("释放下单幂等锁失败，key={}，会在TTL到期后自然失效", lockKey, e);
                }
            }
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 幂等保护：网络重试/用户重复点"支付"，同一笔订单不能被处理第二次（否则会重复推送来单提醒，
        // 真实支付场景下更是不能重复走一遍支付成功的业务逻辑）
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_PAID);
        }

        // 暂无微信支付商户资质，无法调用真实的微信支付接口，直接跳过下单并标记为支付成功，方便本地联调后续流程
        paySuccess(ordersPaymentDTO.getOrderNumber());

        return OrderPaymentVO.builder().build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 来单提醒改成发事件到MQ异步推送（见OrderEventNotifyListener），"改状态"和"发通知"解耦，
        // 通知推送的快慢/成败不再影响这个方法本身的响应
        publishOrderEvent(1, ordersDB.getId(), ordersDB.getShopId(), "订单号：" + outTradeNo);
    }

    /**
     * 客户催单
     * @param id
     */
    public void reminder(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 客户催单同理，发事件到MQ异步推送
        publishOrderEvent(2, ordersDB.getId(), ordersDB.getShopId(), "订单号：" + ordersDB.getNumber());
    }

    /**
     * 发布订单事件（来单提醒type=1/客户催单type=2）到MQ，由OrderEventNotifyListener异步消费并推送WebSocket。
     * messageId用于消费端去重（MQ只保证至少一次投递）。
     * RabbitMQ不可达时不能让通知发不出去这件事拖垮下单/催单接口本身，记日志、跳过推送，fail-open。
     */
    private void publishOrderEvent(int type, Long orderId, Long shopId, String content) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("messageId", UUID.randomUUID().toString());
            message.put("type", type);
            message.put("orderId", orderId);
            message.put("shopId", shopId);
            message.put("content", content);
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_EVENT_NOTIFY_ROUTING_KEY, message);
        } catch (Exception e) {
            log.error("订单事件消息发送失败（RabbitMQ不可达），本次通知推送跳过，订单本身的状态变更不受影响", e);
        }
    }

    /**
     * 用户端订单分页查询
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    /**
     * 游标分页：不做COUNT(*)，按(order_time desc, id desc)排keyset。cursorId为null查第一页，
     * 否则查"排在上一页最后一条记录之后"的下一批——historyOrders这种连续下滑的场景不需要跳页，
     * 换掉offset分页省下"翻到第N页都要先数一遍总数"这个随数据量增长而变贵的开销（见REPORT.md）
     */
    public CursorPageResult pageQuery4User(Long cursorId, int limit, Integer status) {
        Long userId = BaseContext.getCurrentId();

        // 客户端只传cursorId，不传orderTime——JSON响应里的orderTime是格式化到分钟的（见JacksonObjectMapper），
        // 拿这个截断过的字符串去跟数据库里精确到秒的order_time比较会算错分页边界，所以这里用cursorId
        // 反查一次这条记录真实的order_time，用数据库里的精确值做keyset比较，不依赖客户端回传的精度
        LocalDateTime cursorOrderTime = null;
        if (cursorId != null) {
            Orders cursorOrder = orderMapper.getById(cursorId);
            if (cursorOrder != null) {
                cursorOrderTime = cursorOrder.getOrderTime();
            }
            // 查不到（比如传了个不存在的id）就当成没传cursor，退化成查第一页，不抛异常
        }

        // 多查1条，用来判断是否还有下一页，不用额外发一次COUNT
        List<Orders> rows = orderMapper.pageQueryByCursorForUser(userId, status, cursorOrderTime, cursorId, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<Orders> pageRows = hasMore ? rows.subList(0, limit) : rows;

        List<OrderVO> list = new ArrayList<>();
        for (Orders orders : pageRows) {
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders.getId());
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            orderVO.setOrderDetailList(orderDetails);
            list.add(orderVO);
        }
        return new CursorPageResult(list, hasMore);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    public void userCancelById(Long id) throws Exception {
        // "读状态判断能不能取消、再写"这整段要在锁里做，不能只锁最后的update——
        // 不然还是会出现两条路径都读到"能取消"的旧状态、各自都认为自己能写的竞态窗口
        runWithCancelLock(id, () -> {
            Orders ordersDB = orderMapper.getById(id);

            if (ordersDB == null) {
                throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
            }

            //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
            if (ordersDB.getStatus() > 2) {
                throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }

            Orders orders = new Orders();
            orders.setId(ordersDB.getId());

            // 订单处于待接单状态下取消，需要进行退款
            if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
                // 暂无微信支付商户资质，无法调用真实的微信退款接口，直接跳过并标记为已退款，逻辑同 payment() 里的处理
                orders.setPayStatus(Orders.REFUND);
            }

            // 更新订单状态、取消原因、取消时间——用CAS版本兜底：就算锁没能如预期生效，status已经
            // 不在CANCELLABLE_BY_USER里时这条UPDATE影响0行，不会覆盖别的路径已经写好的结果
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason("用户取消");
            orders.setCancelTime(LocalDateTime.now());
            if (orderMapper.updateWithStatusGuard(orders, CANCELLABLE_BY_USER) == 0) {
                verifyAlreadyCancelledOrThrow(id);
                return;
            }

            // 下单时扣了库存，取消了就要加回去
            restoreStockForOrder(id);
        });
    }

    /**
     * 再来一单
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询原订单（取其店铺id）及订单详情
        Orders originalOrder = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setShopId(originalOrder.getShopId());
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量加入购物车（现在存Redis，不是数据库，且购物车没有跟着订单一起搬，问sky-server要）
        skyServerClient.addAllToCart(userId, shoppingCartList);
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        ordersPageQueryDTO.setShopId(BaseContext.getCurrentShopId());
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream()
                .map(x -> x.getName() + "*" + x.getNumber() + ";")
                .collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量（shopId为null时平台超管可查看全平台数据）
        Long shopId = BaseContext.getCurrentShopId();
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED, shopId);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED, shopId);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS, shopId);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        checkOrderBelongsToCurrentShop(ordersConfirmDTO.getId());

        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 校验订单是否存在且归属当前登录员工所在店铺，防止跨店操作订单（平台超管无具体店铺，不允许操作订单数据）
     * @param orderId
     */
    private void checkOrderBelongsToCurrentShop(Long orderId) {
        Orders ordersDB = orderMapper.getById(orderId);
        Long shopId = BaseContext.getCurrentShopId();
        if (ordersDB == null || shopId == null || !shopId.equals(ordersDB.getShopId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 校验订单归属当前店铺，防止跨店操作，不涉及订单状态的读-判断-写，不用放进锁里
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        Long shopId = BaseContext.getCurrentShopId();
        if (ordersDB == null || shopId == null || !shopId.equals(ordersDB.getShopId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 拒单跟用户取消/管理端取消/超时自动取消是同一组会互相竞争同一个订单的"取消"路径，之前这里
        // 没有加锁——引入库存恢复之后，拒单和管理端取消撞在同一个订单上会导致库存被恢复两次，
        // 所以这次一并补上锁
        runWithCancelLock(ordersRejectionDTO.getId(), () -> {
            Orders latest = orderMapper.getById(ordersRejectionDTO.getId());

            // 订单只有存在且状态为2（待接单）才可以拒单
            if (latest == null || !latest.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
                throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }

            //支付状态
            if (Orders.PAID.equals(latest.getPayStatus())) {
                // 暂无微信支付商户资质，无法调用真实的微信退款接口，跳过并只记录日志
                log.info("订单{}已支付，拒单需要退款（未接入真实微信支付，跳过实际退款调用）", latest.getNumber());
            }

            // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
            Orders orders = new Orders();
            orders.setId(latest.getId());
            orders.setStatus(Orders.CANCELLED);
            orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
            orders.setCancelTime(LocalDateTime.now());
            if (orderMapper.updateWithStatusGuard(orders, REJECTABLE) == 0) {
                verifyAlreadyCancelledOrThrow(latest.getId());
                return;
            }

            // 下单时扣了库存，拒单了就要加回去
            restoreStockForOrder(latest.getId());
        });
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 归属校验不用放进锁里（不涉及订单状态的读-判断-写），锁只包住真正有竞态的这一段
        checkOrderBelongsToCurrentShop(ordersCancelDTO.getId());

        runWithCancelLock(ordersCancelDTO.getId(), () -> {
            Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

            // 已经是取消状态就不再重复处理——不加这个判断的话，管理端对同一笔订单连续点两次"取消"
            // 会把已经恢复过的库存再恢复一次，凭空多出库存来，是引入库存恢复之后才需要补的判断
            if (ordersDB == null || Orders.CANCELLED.equals(ordersDB.getStatus())) {
                return;
            }

            //支付状态
            Integer payStatus = ordersDB.getPayStatus();
            if (payStatus.equals(Orders.PAID)) {
                // 暂无微信支付商户资质，无法调用真实的微信退款接口，跳过并只记录日志
                log.info("订单{}已支付，管理端取消需要退款（未接入真实微信支付，跳过实际退款调用）", ordersDB.getNumber());
            }

            // 管理端取消订单需要退款，根据订单id更新订单状态、取消原因、取消时间
            Orders orders = new Orders();
            orders.setId(ordersCancelDTO.getId());
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason(ordersCancelDTO.getCancelReason());
            orders.setCancelTime(LocalDateTime.now());
            // 0行说明在我们读到ordersDB之后、写之前，别的路径已经把它改成CANCELLED了——
            // 跟上面"已经是取消状态就跳过"是同一种情况，静默跳过就好，不需要抛异常打断管理端操作
            if (orderMapper.updateWithStatusGuard(orders, CANCELLABLE_BY_SHOP) == 0) {
                log.info("订单{}管理端取消时CAS未命中（已被别的路径改变状态），跳过", ordersCancelDTO.getId());
                return;
            }

            // 下单时扣了库存，取消了就要加回去
            restoreStockForOrder(ordersCancelDTO.getId());
        });
    }

    /**
     * 派送订单
     * @param id
     */
    public void delivery(Long id) {
        // 根据id查询订单，并校验归属当前店铺
        checkOrderBelongsToCurrentShop(id);
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     * @param id
     */
    public void complete(Long id) {
        // 根据id查询订单，并校验归属当前店铺
        checkOrderBelongsToCurrentShop(id);
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }
}
