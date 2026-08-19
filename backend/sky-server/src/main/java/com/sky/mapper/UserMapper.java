package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.sky.dto.DailyUserStatDTO;
import com.sky.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    @Select("select * from user where username = #{username}")
    User getByUsername(String username);

    /**
     * 插入用户数据
     * @param user
     */
    void insert(User user);

    /**
     * 根据id查询用户
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 根据条件统计用户数量
     * @param map 包含 begin、end（注册时间区间，可选）
     */
    Integer countByMap(Map<String, Object> map);

    /**
     * 按天分组统计时间区间内每天的新增用户数，一次查询代替按天循环查询
     * @param begin
     * @param end
     */
    List<DailyUserStatDTO> countGroupByDate(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
