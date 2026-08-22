package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ReviewDTO;
import com.sky.dto.ReviewPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.entity.Review;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ReviewBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ReviewMapper;
import com.sky.result.PageResult;
import com.sky.service.ReviewService;
import com.sky.vo.ReviewVO;
import com.sky.vo.ShopRatingSummaryVO;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final String RATING_SUMMARY_CACHE = "shopRatingSummaryCache";

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final CacheManager cacheManager;

    ReviewServiceImpl(ReviewMapper reviewMapper, OrderMapper orderMapper, CacheManager cacheManager) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.cacheManager = cacheManager;
    }

    public void submit(ReviewDTO reviewDTO) {
        Orders order = orderMapper.getById(reviewDTO.getOrderId());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(BaseContext.getCurrentId())) {
            throw new ReviewBusinessException(MessageConstant.REVIEW_ORDER_NOT_OWNED);
        }
        if (!order.getStatus().equals(Orders.COMPLETED)) {
            throw new ReviewBusinessException(MessageConstant.REVIEW_ORDER_NOT_COMPLETED);
        }
        if (reviewMapper.getByOrderId(reviewDTO.getOrderId()) != null) {
            throw new ReviewBusinessException(MessageConstant.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .orderId(reviewDTO.getOrderId())
                .shopId(order.getShopId())
                .userId(order.getUserId())
                .rating(reviewDTO.getRating())
                .content(reviewDTO.getContent())
                .images(reviewDTO.getImages())
                .createTime(LocalDateTime.now())
                .build();
        reviewMapper.insert(review);

        // 这条评价改变了该店铺的平均分，把汇总缓存清掉，下次查询重新计算一次并回填缓存（cache-aside写路径）。
        // shopId是方法内查出来的局部变量，不是submit的入参，没法用@CacheEvict的SpEL直接引用，所以用CacheManager手动清
        Cache cache = cacheManager.getCache(RATING_SUMMARY_CACHE);
        if (cache != null) {
            cache.evict(order.getShopId());
        }
    }

    public PageResult pageQueryByShop(Long shopId, ReviewPageQueryDTO reviewPageQueryDTO) {
        reviewPageQueryDTO.setShopId(shopId);
        PageHelper.startPage(reviewPageQueryDTO.getPage(), reviewPageQueryDTO.getPageSize());
        Page<ReviewVO> page = reviewMapper.pageQuery(reviewPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public PageResult pageQueryForAdmin(ReviewPageQueryDTO reviewPageQueryDTO) {
        reviewPageQueryDTO.setShopId(BaseContext.getCurrentShopId());
        PageHelper.startPage(reviewPageQueryDTO.getPage(), reviewPageQueryDTO.getPageSize());
        Page<ReviewVO> page = reviewMapper.pageQuery(reviewPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    public void reply(Long reviewId, String replyContent) {
        Long shopId = BaseContext.getCurrentShopId();
        if (shopId == null) {
            throw new ReviewBusinessException(MessageConstant.SHOP_SCOPED_ONLY);
        }
        int affected = reviewMapper.updateReply(reviewId, replyContent, LocalDateTime.now(), shopId);
        if (affected == 0) {
            throw new ReviewBusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }
    }

    // 之前是每次都对review表实时avg()+count()聚合，店铺列表接口对每个店铺都要跑一次这个查询；
    // 改成读缓存、提交评价时清缓存，读多写少的场景下不再需要每次现算
    @Cacheable(cacheNames = RATING_SUMMARY_CACHE, key = "#shopId")
    public ShopRatingSummaryVO getShopRatingSummary(Long shopId) {
        ShopRatingSummaryVO summary = reviewMapper.getShopRatingSummary(shopId);
        if (summary == null || summary.getReviewCount() == null) {
            return ShopRatingSummaryVO.builder().avgRating(null).reviewCount(0).build();
        }
        return summary;
    }
}
