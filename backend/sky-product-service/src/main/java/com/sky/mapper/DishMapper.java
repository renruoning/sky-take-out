package com.sky.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;

@Mapper
public interface DishMapper {
    /**
     * 根据id查询菜品信息（不限定店铺：供购物车/订单等按主键直接取详情的内部场景使用；
     * 管理端“根据ID查询菜品”接口在Service层额外校验了shopId归属，见DishServiceImpl.getByIdWithFlavor）
     *
     * @param id 菜品id
     * @return 菜品信息
     */
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    /**
     * 根据id删除单个菜品信息
     *
     * @param id 菜品id
     */
    @Delete("delete from dish where id = #{id}")
    void deleteById(Long id);

    /**
     * 根据分类id查询菜品数量
     * 
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入菜品信息
     * 
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 分页查询菜品信息
     * 
     * @param dishPageQueryDTO
     * @return
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id批量删除菜品信息（限定店铺）
     *
     * @param ids 菜品id列表
     * @param shopId 所属店铺id
     */
    void deleteByIds(@Param("ids") List<Long> ids, @Param("shopId") Long shopId);

    /**
     * 更新菜品信息
     * 
     * @param dish 菜品信息
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(Dish dish);

    /**
     * 动态条件查询菜品
     * @param dish
     * @return
    */
    List<Dish> list(Dish dish);

    /**
     * 根据套餐id查询菜品
     * @param setmealId
     * @return
    */
    @Select("select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = #{setmealId}")
    List<Dish> getBySetmealId(Long setmealId);

    /**
     * 根据条件统计菜品数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 原子扣减库存：库存不够（或id不存在）时WHERE条件不成立，影响行数为0，
     * 不会出现扣成负数——不用先SELECT再UPDATE两步判断，避免并发场景下的竞态
     * @param id 菜品id
     * @param number 扣减数量
     * @return 影响行数，0表示库存不足或菜品不存在
     */
    @Update("update dish set stock = stock - #{number} where id = #{id} and stock >= #{number}")
    int deductStock(@Param("id") Long id, @Param("number") Integer number);

    /**
     * 恢复库存（订单取消时的补偿操作），不需要条件判断，直接加回去
     * @param id 菜品id
     * @param number 恢复数量
     */
    @Update("update dish set stock = stock + #{number} where id = #{id}")
    void restoreStock(@Param("id") Long id, @Param("number") Integer number);
}
