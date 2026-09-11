package com.sky.ordernumber;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * 订单号生成：号段模式，取代原来的{@code String.valueOf(System.currentTimeMillis())}——
 * 那种写法没有唯一性保证，两笔订单精确撞在同一毫秒会拿到完全相同的订单号，而orders.number
 * 又被payment()/微信支付回调的OrderMapper.getByNumber()当查找键用，真撞上会导致
 * MyBatis对单对象返回类型的查询抛TooManyResultsException——是真实存在的设计缺陷，不是假设。
 * <p>
 * 号段模式而不是雪花算法：这个项目量级用不上雪花"本地生成、不依赖任何外部存储"这个优势，
 * 换来的代价（处理时钟回拨、多实例部署要解决workerId怎么分配不冲突）对这个项目不划算；
 * 号段模式不依赖机器时钟，多实例部署天然不冲突（见OrderNumberSegmentService），复用现有MySQL
 * 不用引入新组件，订单号也能拼出人可读的日期前缀，权衡下来更贴合这个项目的实际需要。
 * <p>
 * 日期前缀只是给人看的展示信息，不参与唯一性保证——唯一性完全靠后面号段分配出来的id。
 * 应用重启后这里的内存状态会丢失，当前号段里没用完的部分被跳过浪费掉，但订单号不要求
 * 连续无间隙，跳号不影响唯一性，不需要处理
 */
@Component
public class OrderNumberGenerator {

    private static final String BIZ_KEY = "orders";
    private static final int STEP = 1000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderNumberSegmentService orderNumberSegmentService;

    private final Object lock = new Object();
    private long currentId = 0L;
    private long maxId = -1L;

    OrderNumberGenerator(OrderNumberSegmentService orderNumberSegmentService) {
        this.orderNumberSegmentService = orderNumberSegmentService;
    }

    public String nextOrderNumber() {
        long id;
        synchronized (lock) {
            if (currentId > maxId) {
                OrderNumberSegment segment = orderNumberSegmentService.allocateSegment(BIZ_KEY, STEP);
                currentId = segment.getStart();
                maxId = segment.getEnd();
            }
            id = currentId++;
        }
        return DATE_FORMAT.format(LocalDate.now()) + id;
    }
}
