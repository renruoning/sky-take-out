package com.sky.service;

import com.sky.dto.ReviewDTO;
import com.sky.dto.ReviewPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.ShopRatingSummaryVO;

import java.util.List;
import java.util.Map;

public interface ReviewService {

    /**
     * 用户提交订单评价
     * @param reviewDTO
     */
    void submit(ReviewDTO reviewDTO);

    /**
     * 用户端查看某店铺的评价列表（公开）
     * @param shopId
     * @param reviewPageQueryDTO
     * @return
     */
    PageResult pageQueryByShop(Long shopId, ReviewPageQueryDTO reviewPageQueryDTO);

    /**
     * 管理端查看评价（当前登录员工所属店铺；平台超管看全平台）
     * @param reviewPageQueryDTO
     * @return
     */
    PageResult pageQueryForAdmin(ReviewPageQueryDTO reviewPageQueryDTO);

    /**
     * 商家回复评价
     * @param reviewId
     * @param replyContent
     */
    void reply(Long reviewId, String replyContent);

    /**
     * 查询店铺的评价聚合数据（平均分+评价数）
     * @param shopId
     * @return
     */
    ShopRatingSummaryVO getShopRatingSummary(Long shopId);

    /**
     * 批量查询多个店铺的评价聚合数据，供sky-server的批量RPC接口用，避免逐店铺远程调用的N+1
     * @param shopIds
     * @return
     */
    Map<Long, ShopRatingSummaryVO> getShopRatingSummaryBatch(List<Long> shopIds);
}
