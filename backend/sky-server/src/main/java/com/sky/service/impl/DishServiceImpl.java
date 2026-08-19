package com.sky.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.entity.Setmeal;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;
    private final DishFlavorMapper dishFlavorMapper;
    private final SetmealDishMapper setmealDishMapper;
    private final SetmealMapper setmealMapper;

    DishServiceImpl(DishMapper dishMapper, SetmealDishMapper setmealDishMapper, DishFlavorMapper dishFlavorMapper,
            SetmealMapper setmealMapper) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.setmealDishMapper = setmealDishMapper;
        this.setmealMapper = setmealMapper;
    }

    /**
     * 保存菜品及其口味信息
     * 
     * @param dishDTO 菜品信息
     */
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        // 保存菜品信息到菜品表
        log.info("保存菜品信息: {}", dishDTO);
        dishMapper.insert(dish);

        // 获取insert生成的主键值
        Long dishId = dish.getId();

        // 保存口味信息到口味表
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(dishFlavor -> dishFlavor.setDishId(dishId));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 批量(或单个）删除菜品及其口味信息
     * 
     * @param ids 菜品ID列表
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        Long shopId = BaseContext.getCurrentShopId();
        // 判断当前菜品是否能删除--是否为起售中的菜品，同时校验菜品归属店铺，防止跨店删除
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish == null || !dish.getShopId().equals(shopId)) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_NOT_FOUND);
            }
            if (dish.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        // 判断当前菜品是否能删除--是否关联了套餐
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        // 删除菜品表中的菜品信息
        dishMapper.deleteByIds(ids, shopId);
        // 删除口味表中的口味信息
        dishFlavorMapper.deleteByDishIds(ids);
    }

    /**
     * 菜品分页查询
     * 
     * @param dishPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        dishPageQueryDTO.setShopId(BaseContext.getCurrentShopId());
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据ID查询菜品信息和口味信息
     * 
     * @param id 菜品ID
     * @return 菜品信息和口味信息
     */
    @Transactional(readOnly = true)
    public DishVO getByIdWithFlavor(Long dishId) {
        // 查询菜品信息，并校验归属店铺，防止跨店查看
        Dish dish = dishMapper.getById(dishId);
        if (dish == null || !dish.getShopId().equals(BaseContext.getCurrentShopId())) {
            return null;
        }

        // 查询口味信息, 一个菜品可能对应多个口味
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(dishId);

        // 封装成DishVO对象
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 根据ID修改菜品信息和口味信息
     * 
     * @param dishDTO 菜品信息
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        // 更新菜品表中的菜品信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dish.setShopId(BaseContext.getCurrentShopId());
        dishMapper.update(dish);

        // 删除口味表中原有的口味信息
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        // 重新插入口味表中新的口味信息
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach(dishFlavor -> dishFlavor.setDishId(dishDTO.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
    */
    public List<Dish> list(Long categoryId) {
        Dish dish = Dish.builder()
            .categoryId(categoryId)
            .status(StatusConstant.ENABLE)
            .shopId(BaseContext.getCurrentShopId())
            .build();
        return dishMapper.list(dish);
    }

    /**
     * 起售、停售菜品
     * @param status
     * @param id
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        Long shopId = BaseContext.getCurrentShopId();
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .shopId(shopId)
                .build();
        dishMapper.update(dish);

        // 如果是停售菜品，还需要将包含该菜品的套餐一并停售
        if (StatusConstant.DISABLE.equals(status)) {
            List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(List.of(id));
            if (setmealIds != null && !setmealIds.isEmpty()) {
                for (Long setmealId : setmealIds) {
                    Setmeal setmeal = Setmeal.builder()
                            .id(setmealId)
                            .status(StatusConstant.DISABLE)
                            .shopId(shopId)
                            .build();
                    setmealMapper.update(setmeal);
                }
            }
        }
    }
}
