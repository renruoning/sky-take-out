package com.sky.ordernumber;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sky.mapper.OrderNumberSegmentMapper;

/**
 * 号段分配。必须是一个独立的Spring bean被外部调用（不能挪成OrderNumberGenerator类里的
 * 私有/自调用方法）——@Transactional靠Spring AOP代理生效，同一个类内部方法互相调用
 * 不经过代理，事务会静默失效，这是接read-write-split时聊过的同一类坑，这里真实应用一次
 */
@Service
public class OrderNumberSegmentService {

    private final OrderNumberSegmentMapper orderNumberSegmentMapper;

    OrderNumberSegmentService(OrderNumberSegmentMapper orderNumberSegmentMapper) {
        this.orderNumberSegmentMapper = orderNumberSegmentMapper;
    }

    /**
     * 一次UPDATE把max_id原子地往前推一个step，再读回新值——
     * [新max_id - step + 1, 新max_id]这个区间就是这次独占拿到的号段。
     * UPDATE这一步的行锁保证了并发调用（同一个实例内的多线程、或者多个部署实例）
     * 分到的号段天然不会重叠，不需要额外的分布式锁
     */
    @Transactional
    public OrderNumberSegment allocateSegment(String bizKey, int step) {
        orderNumberSegmentMapper.incrementMaxId(bizKey, step);
        long newMaxId = orderNumberSegmentMapper.getMaxId(bizKey);
        return new OrderNumberSegment(newMaxId - step + 1, newMaxId);
    }
}
