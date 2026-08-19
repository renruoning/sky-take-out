package com.sky.mapper;

import com.sky.entity.AiMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiMessageMapper {

    /**
     * 新增消息
     * @param aiMessage
     */
    @Insert("insert into ai_message (conversation_id, role, content, create_time) " +
            "values (#{conversationId}, #{role}, #{content}, #{createTime})")
    void insert(AiMessage aiMessage);

    /**
     * 查询某会话的完整消息记录，按时间升序（用来拼AI请求的历史上下文，也用来给前端展示对话）
     * @param conversationId
     * @return
     */
    @Select("select * from ai_message where conversation_id = #{conversationId} order by create_time asc")
    List<AiMessage> listByConversationId(Long conversationId);
}
