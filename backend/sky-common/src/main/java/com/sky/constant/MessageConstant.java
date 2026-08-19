package com.sky.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String DISH_NOT_FOUND = "菜品不存在";
    public static final String SETMEAL_NOT_FOUND = "套餐不存在";
    public static final String SHOP_PLATFORM_ONLY = "仅平台超管可操作店铺信息";
    public static final String SHOP_SCOPED_ONLY = "平台超管无法操作具体店铺数据，请以店铺员工身份登录";
    public static final String SHOPPING_CART_SHOP_CONFLICT = "购物车中含有其他店铺的商品，请先清空购物车";
    public static final String PLATFORM_MUST_SPECIFY_SHOP = "平台超管新增员工时必须指定所属店铺shopId";
    public static final String SHOP_BUSINESS_TYPE_REQUIRED = "新增店铺必须指定主营业类型";
    public static final String SHOP_BUSINESS_TYPE_DUPLICATE = "副营业类型不能与主营业类型相同";
    public static final String SHOP_BUSINESS_TYPE_COOLDOWN = "距离上次修改营业类型不足一年，暂不能再次修改";
    public static final String REVIEW_ORDER_NOT_COMPLETED = "订单未完成，暂不能评价";
    public static final String REVIEW_ORDER_NOT_OWNED = "只能评价自己的订单";
    public static final String REVIEW_ALREADY_EXISTS = "该订单已评价过";
    public static final String REVIEW_NOT_FOUND = "评价不存在";
    public static final String AI_CONVERSATION_NOT_FOUND = "会话不存在";
    public static final String AI_SERVICE_UNAVAILABLE = "AI服务暂时不可用，请稍后再试";

}
