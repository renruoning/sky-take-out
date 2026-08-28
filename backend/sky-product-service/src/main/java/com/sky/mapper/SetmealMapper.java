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
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询套餐的数量
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    /**
     * 动态条件查询套餐
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);
	
	/**
     * 根据套餐id查询菜品选项
     * @param setmealId
     * @return
     */
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);

    /**
     * 新增套餐
     * @param setmeal
    */
    @AutoFill(OperationType.INSERT)
    void insert(Setmeal setmeal);

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
    */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据id查询套餐
     * @param id
     * @return
    */
    @Select("select * from setmeal where id = #{id}")
    Setmeal getById(Long id);

    /**
         * 根据id删除套餐（限定店铺）
         * @param setmealId
         * @param shopId
    */
    @Delete("delete from setmeal where id = #{id} and shop_id = #{shopId}")
    void deleteById(@Param("id") Long setmealId, @Param("shopId") Long shopId);

    /**
     * 根据id修改套餐
     * @param setmeal
    */
    @AutoFill(OperationType.UPDATE)
    void update(Setmeal setmeal);

    /**
     * 根据条件统计套餐数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 原子扣减库存，见DishMapper.deductStock同款逻辑
     * @param id 套餐id
     * @param number 扣减数量
     * @return 影响行数，0表示库存不足或套餐不存在
     */
    @Update("update setmeal set stock = stock - #{number} where id = #{id} and stock >= #{number}")
    int deductStock(@Param("id") Long id, @Param("number") Integer number);

    /**
     * 恢复库存（订单取消时的补偿操作）
     * @param id 套餐id
     * @param number 恢复数量
     */
    @Update("update setmeal set stock = stock + #{number} where id = #{id}")
    void restoreStock(@Param("id") Long id, @Param("number") Integer number);
}
