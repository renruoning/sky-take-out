package com.sky.controller.user;

import com.sky.entity.Category;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
public class CategoryController {

    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询分类
     * @param type
     * @param shopId 店铺id（用户端需显式选择店铺）
     * @return
     */
    @GetMapping("/list")
    public Result<List<Category>> list(Integer type, Long shopId) {
        List<Category> list = categoryService.list(type, shopId);
        return Result.success(list);
    }
}
