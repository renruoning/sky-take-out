package com.sky.mapper;

import com.sky.entity.AiConversation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiConversationMapper {

    /**
     * 新增会话
     * @param aiConversation
     */
    @Insert("insert into ai_conversation (user_id, title, create_time, update_time) " +
            "values (#{userId}, #{title}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiConversation aiConversation);

    /**
     * 根据id查询会话
     * @param id
     * @return
     */
    @Select("select * from ai_conversation where id = #{id}")
    AiConversation getById(Long id);

    /**
     * 查询当前用户的会话列表，按更新时间倒序
     * @param userId
     * @return
     */
    @Select("select * from ai_conversation where user_id = #{userId} order by update_time desc")
    List<AiConversation> listByUserId(Long userId);

    /**
     * 更新会话的最后更新时间（每次新消息后刷新，供会话列表排序用）
     * @param aiConversation
     */
    @Update("update ai_conversation set update_time = #{updateTime} where id = #{id}")
    void updateTime(AiConversation aiConversation);
}
