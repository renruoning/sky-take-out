package com.sky.controller.user;

import com.sky.dto.AiChatDTO;
import com.sky.entity.AiConversation;
import com.sky.entity.AiMessage;
import com.sky.result.Result;
import com.sky.service.AiChatService;
import com.sky.vo.AiChatVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userAiChatController")
@RequestMapping("/user/ai")
@Slf4j
@Api(tags = "C端-AI客服接口")
public class AiChatController {

    private final AiChatService aiChatService;

    AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 发送一条消息，返回AI回复
     * @param aiChatDTO
     * @return
     */
    @PostMapping("/chat")
    @ApiOperation("发送AI客服消息")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO aiChatDTO) {
        log.info("AI客服对话: {}", aiChatDTO);
        AiChatVO aiChatVO = aiChatService.chat(aiChatDTO);
        return Result.success(aiChatVO);
    }

    /**
     * 查询当前用户的会话列表
     * @return
     */
    @GetMapping("/conversations")
    @ApiOperation("查询AI客服会话列表")
    public Result<List<AiConversation>> conversations() {
        return Result.success(aiChatService.listConversations());
    }

    /**
     * 查询某会话的完整消息记录
     * @param conversationId
     * @return
     */
    @GetMapping("/messages")
    @ApiOperation("查询AI客服会话消息记录")
    public Result<List<AiMessage>> messages(Long conversationId) {
        return Result.success(aiChatService.listMessages(conversationId));
    }
}
