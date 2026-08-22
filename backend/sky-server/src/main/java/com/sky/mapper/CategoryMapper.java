package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.enumeration.OperationType;
import com.sky.annotation.AutoFill;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 判断某个分类id是否真实存在（用于菜品查询接口挡掉编造分类id的缓存穿透攻击，不用等到查dish表才发现是空的）
     * @param id
     * @return
     */
    @Select("select count(1) from category where id = #{id}")
    int existsById(Long id);

    /**
     * 插入数据
     *
     * @param category
     */
    @Insert("insert into category(shop_id, type, name, sort, status, create_time, update_time, create_user, update_user)" +
            " VALUES" +
            " (#{shopId}, #{type}, #{name}, #{sort}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @AutoFill(OperationType.INSERT)
    void insert(Category category);

    /**
     * 分页查询（按categoryPageQueryDTO.shopId过滤）
     *
     * @param categoryPageQueryDTO
     * @return
     */
    Page<Category> pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 根据id删除分类（限定店铺）
     *
     * @param id
     * @param shopId
     */
    @Delete("delete from category where id = #{id} and shop_id = #{shopId}")
    void deleteById(@Param("id") Long id, @Param("shopId") Long shopId);

    /**
     * 根据id修改分类（限定店铺，防止跨店修改）
     *
     * @param category
     */
    @AutoFill(OperationType.UPDATE)
    void update(Category category);

    /**
     * 根据类型查询分类（限定店铺）
     *
     * @param type
     * @param shopId
     * @return
     */
    List<Category> list(@Param("type") Integer type, @Param("shopId") Long shopId);
}
