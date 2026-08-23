package com.sky.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
