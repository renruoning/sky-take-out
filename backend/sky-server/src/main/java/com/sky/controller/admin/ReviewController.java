package com.sky.controller.admin;

import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.dto.ReviewPageQueryDTO;
import com.sky.dto.ReviewReplyDTO;

@RestController("adminReviewController")
@RequestMapping("/admin/review")
@Slf4j
@Api(tags = "评价管理接口")
public class ReviewController {

    private final ReviewService reviewService;

    ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 分页查询本店（超管为全平台）评价
     * @param reviewPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("评价分页查询")
    public Result<PageResult> page(ReviewPageQueryDTO reviewPageQueryDTO) {
        PageResult pageResult = reviewService.pageQueryForAdmin(reviewPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 商家回复评价
     * @param reviewReplyDTO
     * @return
     */
    @PutMapping("/reply")
    @ApiOperation("商家回复评价")
    public Result reply(@RequestBody ReviewReplyDTO reviewReplyDTO) {
        reviewService.reply(reviewReplyDTO.getId(), reviewReplyDTO.getReply());
        return Result.success();
    }
}
