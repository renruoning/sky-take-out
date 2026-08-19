package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.ReviewPageQueryDTO;
import com.sky.entity.Review;
import com.sky.vo.ReviewVO;
import com.sky.vo.ShopRatingSummaryVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReviewMapper {

    /**
     * 新增评价
     * @param review
     */
    @Insert("insert into review(order_id, shop_id, user_id, rating, content, images, create_time)" +
            " values (#{orderId}, #{shopId}, #{userId}, #{rating}, #{content}, #{images}, #{createTime})")
    void insert(Review review);

    /**
     * 根据订单id查询评价（判断是否已评价过）
     * @param orderId
     * @return
     */
    @Select("select * from review where order_id = #{orderId}")
    Review getByOrderId(Long orderId);

    /**
     * 分页查询评价（带用户名/头像/订单号），shopId为null时不限定店铺（平台超管查看全平台）
     * @param reviewPageQueryDTO
     * @return
     */
    Page<ReviewVO> pageQuery(ReviewPageQueryDTO reviewPageQueryDTO);

    /**
     * 商家回复评价，限定shop_id防止跨店回复
     * @param id
     * @param reply
     * @param replyTime
     * @param shopId
     */
    int updateReply(@Param("id") Long id, @Param("reply") String reply,
                     @Param("replyTime") java.time.LocalDateTime replyTime, @Param("shopId") Long shopId);

    /**
     * 查询店铺的评价聚合数据（平均分+评价数）
     * @param shopId
     * @return
     */
    ShopRatingSummaryVO getShopRatingSummary(Long shopId);
}
