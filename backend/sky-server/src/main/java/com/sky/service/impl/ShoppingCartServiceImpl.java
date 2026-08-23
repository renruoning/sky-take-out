package com.sky.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.sky.client.ProductClient;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;

/**
 * 购物车存Redis Hash，不再落MySQL：高频写、可以容忍丢失、天然需要TTL过期，这几个特征
 * 决定了购物车更适合Redis而不是关系型数据库（跟菜单缓存的道理类似但更彻底——菜单是缓存，
 * 购物车这里直接就是唯一数据源了）。
 * <p>
 * key: shopping_cart:{userId}，field: 按dishId/setmealId/dishFlavor算出的稳定标识，value: 整行JSON。
 * 每次写操作后刷新一次整个key的TTL，实现"多久不动购物车就自动清空"，不需要额外的定时清理任务。
 * <p>
 * 这个功能现在完全依赖Redis可用——不像P0那些"Redis挂了就降级"的场景，购物车没有MySQL兜底，
 * Redis不可用时购物车功能本身就不可用，这是迁移到Redis必然带来的取舍，不是遗漏。
 */
@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private static final String KEY_PREFIX = "shopping_cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductClient productClient;

    ShoppingCartServiceImpl(StringRedisTemplate stringRedisTemplate, ProductClient productClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.productClient = productClient;
    }

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        String key = cartKey(userId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        // 购物车不能同时装多个店铺的商品：若购物车非空且已有商品属于其他店铺，提示先清空
        Map<String, String> existing = hashOps.entries(key);
        if (!existing.isEmpty()) {
            ShoppingCart any = JSON.parseObject(existing.values().iterator().next(), ShoppingCart.class);
            if (!any.getShopId().equals(shoppingCartDTO.getShopId())) {
                throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_SHOP_CONFLICT);
            }
        }

        String field = itemKey(shoppingCartDTO.getDishId(), shoppingCartDTO.getSetmealId(), shoppingCartDTO.getDishFlavor());
        String existingJson = hashOps.get(key, field);
        if (existingJson != null) {
            // 已存在，数量加一
            ShoppingCart existCart = JSON.parseObject(existingJson, ShoppingCart.class);
            existCart.setNumber(existCart.getNumber() + 1);
            hashOps.put(key, field, JSON.toJSONString(existCart));
            stringRedisTemplate.expire(key, CART_TTL);
            return;
        }

        // 不存在，需要根据是菜品还是套餐查询详情，填充名称、图片、金额后写入
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(userId);
        Long dishId = shoppingCartDTO.getDishId();
        if (dishId != null) {
            Result<Dish> dishResult = productClient.getDish(dishId);
            Dish dish = (dishResult != null && dishResult.getCode() != null && dishResult.getCode() == 1)
                    ? dishResult.getData() : null;
            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
        } else {
            Result<Setmeal> setmealResult = productClient.getSetmeal(shoppingCartDTO.getSetmealId());
            Setmeal setmeal = (setmealResult != null && setmealResult.getCode() != null && setmealResult.getCode() == 1)
                    ? setmealResult.getData() : null;
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        }
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());

        hashOps.put(key, field, JSON.toJSONString(shoppingCart));
        stringRedisTemplate.expire(key, CART_TTL);
    }

    /**
     * 查看当前用户的购物车
     * @return
     */
    public List<ShoppingCart> showShoppingCart() {
        return showShoppingCart(BaseContext.getCurrentId());
    }

    public List<ShoppingCart> showShoppingCart(Long userId) {
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        Map<String, String> entries = hashOps.entries(cartKey(userId));
        List<ShoppingCart> list = new ArrayList<>();
        for (String json : entries.values()) {
            list.add(JSON.parseObject(json, ShoppingCart.class));
        }
        // Redis Hash不保证遍历顺序，用createTime手动排回原来"最新加入的排前面"的语义
        list.sort(Comparator.comparing(ShoppingCart::getCreateTime).reversed());
        return list;
    }

    /**
     * 删除/减少购物车中的一个商品
     * @param shoppingCartDTO
     */
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        String key = cartKey(BaseContext.getCurrentId());
        String field = itemKey(shoppingCartDTO.getDishId(), shoppingCartDTO.getSetmealId(), shoppingCartDTO.getDishFlavor());
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        String json = hashOps.get(key, field);
        if (json == null) {
            return;
        }
        ShoppingCart existCart = JSON.parseObject(json, ShoppingCart.class);
        if (existCart.getNumber() == null || existCart.getNumber() <= 1) {
            hashOps.delete(key, field);
        } else {
            existCart.setNumber(existCart.getNumber() - 1);
            hashOps.put(key, field, JSON.toJSONString(existCart));
            stringRedisTemplate.expire(key, CART_TTL);
        }
    }

    /**
     * 清空当前用户的购物车
     */
    public void cleanShoppingCart() {
        cleanShoppingCart(BaseContext.getCurrentId());
    }

    public void cleanShoppingCart(Long userId) {
        stringRedisTemplate.delete(cartKey(userId));
    }

    /**
     * 批量加入购物车（"再来一单"用）：每个商品的名称/图片/金额已经从原订单详情里带过来了，不用再查一次dish/setmeal表。
     * 已存在的商品数量累加，不存在的直接新增。
     */
    public void addAll(List<ShoppingCart> items) {
        addAll(BaseContext.getCurrentId(), items);
    }

    public void addAll(Long userId, List<ShoppingCart> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String key = cartKey(userId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        for (ShoppingCart item : items) {
            String field = itemKey(item.getDishId(), item.getSetmealId(), item.getDishFlavor());
            String existingJson = hashOps.get(key, field);
            if (existingJson != null) {
                ShoppingCart existCart = JSON.parseObject(existingJson, ShoppingCart.class);
                existCart.setNumber(existCart.getNumber() + item.getNumber());
                hashOps.put(key, field, JSON.toJSONString(existCart));
            } else {
                item.setUserId(userId);
                hashOps.put(key, field, JSON.toJSONString(item));
            }
        }
        stringRedisTemplate.expire(key, CART_TTL);
    }

    private String cartKey(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String itemKey(Long dishId, Long setmealId, String dishFlavor) {
        if (dishId != null) {
            return "dish:" + dishId + ":" + (dishFlavor == null ? "" : dishFlavor);
        }
        return "setmeal:" + setmealId;
    }
}
