package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.ShopPageQueryDTO;
import com.sky.entity.Shop;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShopMapper {

    /**
     * 新增店铺
     *
     * @param shop
     */
    @Insert("insert into shop(name, address, phone, status, create_time, update_time, create_user, update_user)" +
            " VALUES" +
            " (#{name}, #{address}, #{phone}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @AutoFill(OperationType.INSERT)
    void insert(Shop shop);

    /**
     * 分页查询（平台超管专用，不限定店铺）
     *
     * @param shopPageQueryDTO
     * @return
     */
    Page<Shop> pageQuery(ShopPageQueryDTO shopPageQueryDTO);

    /**
     * 修改店铺信息
     *
     * @param shop
     */
    @AutoFill(OperationType.UPDATE)
    void update(Shop shop);

    /**
     * 根据id查询店铺
     *
     * @param id
     * @return
     */
    @Select("select * from shop where id = #{id}")
    Shop getById(Long id);

    /**
     * 查询所有启用中的店铺（供用户端选店铺使用）
     *
     * @return
     */
    @Select("select * from shop where status = 1 order by id")
    List<Shop> listActive();
}
