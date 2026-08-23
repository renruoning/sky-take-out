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

import java.util.List;

@Mapper
public interface ReviewMapper {

    /**
     * 新增评价，order_number/user_name/user_avatar是提交时从sky-server快照下来的展示字段
     * @param review
     */
    @Insert("insert into review(order_id, shop_id, user_id, order_number, user_name, user_avatar, rating, content, images, create_time)" +
            " values (#{orderId}, #{shopId}, #{userId}, #{orderNumber}, #{userName}, #{userAvatar}, #{rating}, #{content}, #{images}, #{createTime})")
    void insert(Review review);

    /**
     * 根据订单id查询评价（判断是否已评价过）
     * @param orderId
     * @return
     */
    @Select("select * from review where order_id = #{orderId}")
    Review getByOrderId(Long orderId);

    /**
     * 分页查询评价（带快照的用户名/头像/订单号，不再JOIN），shopId为null时不限定店铺（平台超管查看全平台）
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
     * 查询单个店铺的评价聚合数据（平均分+评价数），供@Cacheable的单店铺查询用
     * @param shopId
     * @return
     */
    ShopRatingSummaryVO getShopRatingSummary(Long shopId);

    /**
     * 批量查询多个店铺的评价聚合数据，一条SQL的group by搞定，供sky-server的批量RPC接口用——
     * 避免"店铺列表页对每个店铺都查一次"这种N+1，不管是进程内调用还是跨服务调用都一样是N+1
     * @param shopIds
     * @return
     */
    List<ShopRatingSummaryVO> getShopRatingSummaryBatch(@Param("shopIds") List<Long> shopIds);
}
