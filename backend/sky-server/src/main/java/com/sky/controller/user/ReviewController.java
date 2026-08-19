package com.sky.controller.user;

import com.sky.dto.ReviewDTO;
import com.sky.dto.ReviewPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userReviewController")
@RequestMapping("/user/review")
@Slf4j
@Api(tags = "C端-订单评价接口")
public class ReviewController {

    private final ReviewService reviewService;

    ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 提交订单评价
     * @param reviewDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("提交订单评价")
    public Result submit(@RequestBody ReviewDTO reviewDTO) {
        log.info("提交订单评价: {}", reviewDTO);
        reviewService.submit(reviewDTO);
        return Result.success();
    }

    /**
     * 查看某店铺的评价列表（公开）
     * @param shopId
     * @param reviewPageQueryDTO
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查看店铺评价列表")
    public Result<PageResult> list(Long shopId, ReviewPageQueryDTO reviewPageQueryDTO) {
        PageResult pageResult = reviewService.pageQueryByShop(shopId, reviewPageQueryDTO);
        return Result.success(pageResult);
    }
}
