package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.dto.StockChangeItemDTO;
import com.sky.exception.StockBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class StockServiceImpl implements StockService {

    private final DishMapper dishMapper;
    private final SetmealMapper setmealMapper;

    StockServiceImpl(DishMapper dishMapper, SetmealMapper setmealMapper) {
        this.dishMapper = dishMapper;
        this.setmealMapper = setmealMapper;
    }

    /**
     * 一次下单里的多个菜品/套餐在同一个本地事务里逐条扣：只要有一条库存不够（UPDATE影响行数为0），
     * 直接抛异常触发这个事务整体回滚，撤销掉这批里已经扣成功的其它条目——保证"这笔订单涉及的库存"
     * 这个粒度上要么全扣成功要么全不扣，不会出现扣了一半的中间状态。这只解决了product-service自己
     * 这个数据库内部的原子性；order-service那边订单建不建得成功是它自己的事务，两边分属不同数据库，
     * 没法用同一个本地事务盖住——这也是这个项目里"分布式事务"问题真正出现的地方，见order-service的
     * OrderServiceImpl.submit()：那边额外做了一层失败补偿（建单失败时反过来调restoreStock），
     * 用的是手写saga/补偿事务的思路，不是Seata那种真正跨库的分布式事务，留了一个"两边补偿都失败"
     * 的极端情况没有兜底（这正是Seata这类框架真正要解决的问题）。
     */
    @Override
    @Transactional
    public void deductStock(List<StockChangeItemDTO> items) {
        for (StockChangeItemDTO item : items) {
            int rows;
            if (item.getDishId() != null) {
                rows = dishMapper.deductStock(item.getDishId(), item.getNumber());
            } else {
                rows = setmealMapper.deductStock(item.getSetmealId(), item.getNumber());
            }
            if (rows == 0) {
                log.info("库存不足，扣减失败，dishId={}，setmealId={}，number={}",
                        item.getDishId(), item.getSetmealId(), item.getNumber());
                throw new StockBusinessException(MessageConstant.STOCK_NOT_ENOUGH);
            }
        }
    }

    @Override
    @Transactional
    public void restoreStock(List<StockChangeItemDTO> items) {
        for (StockChangeItemDTO item : items) {
            if (item.getDishId() != null) {
                dishMapper.restoreStock(item.getDishId(), item.getNumber());
            } else {
                setmealMapper.restoreStock(item.getSetmealId(), item.getNumber());
            }
        }
    }
}
