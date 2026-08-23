-- P4第四步：把category/dish/dish_flavor/setmeal/setmeal_dish从sky_take_out主库物理拆到独立的
-- sky_product_service库。这五张表不引用orders/user，是干净的原样搬迁，不需要像发票/评价那样
-- 额外回填快照字段。执行完用migration_drop_product_tables_from_main_db.sql清理主库里的旧表

CREATE DATABASE IF NOT EXISTS `sky_product_service` DEFAULT CHARACTER SET utf8mb3 COLLATE utf8_bin;

CREATE TABLE IF NOT EXISTS `sky_product_service`.`category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint NOT NULL COMMENT '所属店铺id',
  `type` int DEFAULT NULL COMMENT '类型   1 菜品分类 2 套餐分类',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int DEFAULT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_shop_name` (`shop_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品及套餐分类';

CREATE TABLE IF NOT EXISTS `sky_product_service`.`dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint NOT NULL COMMENT '所属店铺id',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `status` int DEFAULT '1' COMMENT '0 停售 1 起售',
  `need_prescription` int NOT NULL DEFAULT '0' COMMENT '是否处方药 0否 1是',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_shop_name` (`shop_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品';

CREATE TABLE IF NOT EXISTS `sky_product_service`.`dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`),
  KEY `idx_dish_flavor_dish_id` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品口味关系表';

CREATE TABLE IF NOT EXISTS `sky_product_service`.`setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint NOT NULL COMMENT '所属店铺id',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int DEFAULT '1' COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_shop_name` (`shop_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐';

CREATE TABLE IF NOT EXISTS `sky_product_service`.`setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`),
  KEY `idx_setmeal_dish_setmeal_id` (`setmeal_id`),
  KEY `idx_setmeal_dish_dish_id` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐菜品关系';

INSERT INTO `sky_product_service`.`category` SELECT * FROM `sky_take_out`.`category`;
INSERT INTO `sky_product_service`.`dish` SELECT * FROM `sky_take_out`.`dish`;
INSERT INTO `sky_product_service`.`dish_flavor` SELECT * FROM `sky_take_out`.`dish_flavor`;
INSERT INTO `sky_product_service`.`setmeal` SELECT * FROM `sky_take_out`.`setmeal`;
INSERT INTO `sky_product_service`.`setmeal_dish` SELECT * FROM `sky_take_out`.`setmeal_dish`;

-- AUTO_INCREMENT对齐，避免新库里下一条insert的自增id跟原库的已有最大值撞在一起
SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`category`;
SET @sql = CONCAT('ALTER TABLE `sky_product_service`.`category` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`dish`;
SET @sql = CONCAT('ALTER TABLE `sky_product_service`.`dish` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`dish_flavor`;
SET @sql = CONCAT('ALTER TABLE `sky_product_service`.`dish_flavor` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`setmeal`;
SET @sql = CONCAT('ALTER TABLE `sky_product_service`.`setmeal` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`setmeal_dish`;
SET @sql = CONCAT('ALTER TABLE `sky_product_service`.`setmeal_dish` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
