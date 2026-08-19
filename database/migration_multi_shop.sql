-- 多商户改造迁移脚本：在已有数据库上执行（幂等性较弱，仅供本项目一次性迁移使用）
USE `sky_take_out`;

-- 1. 新增 shop 表
DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '店铺名称',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺（商户）';

INSERT INTO `shop` (id, name, address, phone, status, create_time, update_time, create_user, update_user) VALUES
  (1, '蜀味阁（示例商户1）', '北京市朝阳区示例路1号', '13800000001', 1, NOW(), NOW(), 1, 1),
  (2, '江南小厨（示例商户2）', '上海市浦东新区示例路2号', '13800000002', 1, NOW(), NOW(), 1, 1);

-- 2. employee 加 shop_id（可空，空=平台超管）
ALTER TABLE `employee` ADD COLUMN `shop_id` bigint NULL COMMENT '所属店铺id，null表示平台超管' AFTER `id`;
UPDATE `employee` SET `shop_id` = 1 WHERE `username` = 'admin';
INSERT INTO `employee` (name, username, password, phone, sex, id_number, status, shop_id, create_time, update_time, create_user, update_user)
VALUES ('平台超管', 'platform', 'e10adc3949ba59abbe56e057f20f883e', '13900000000', '1', '110101199001010099', 1, NULL, NOW(), NOW(), 1, 1)
ON DUPLICATE KEY UPDATE shop_id = NULL;

-- 3. category / dish / setmeal 加 shop_id，并把唯一索引改成 (shop_id, name)
ALTER TABLE `category` ADD COLUMN `shop_id` bigint NOT NULL DEFAULT 1 COMMENT '所属店铺id' AFTER `id`;
ALTER TABLE `category` DROP INDEX `idx_category_name`;
ALTER TABLE `category` ADD UNIQUE KEY `idx_category_shop_name` (`shop_id`, `name`);
ALTER TABLE `category` ALTER COLUMN `shop_id` DROP DEFAULT;

ALTER TABLE `dish` ADD COLUMN `shop_id` bigint NOT NULL DEFAULT 1 COMMENT '所属店铺id' AFTER `id`;
ALTER TABLE `dish` DROP INDEX `idx_dish_name`;
ALTER TABLE `dish` ADD UNIQUE KEY `idx_dish_shop_name` (`shop_id`, `name`);
ALTER TABLE `dish` ALTER COLUMN `shop_id` DROP DEFAULT;

ALTER TABLE `setmeal` ADD COLUMN `shop_id` bigint NOT NULL DEFAULT 1 COMMENT '所属店铺id' AFTER `id`;
ALTER TABLE `setmeal` DROP INDEX `idx_setmeal_name`;
ALTER TABLE `setmeal` ADD UNIQUE KEY `idx_setmeal_shop_name` (`shop_id`, `name`);
ALTER TABLE `setmeal` ALTER COLUMN `shop_id` DROP DEFAULT;

-- 4. shopping_cart / orders 加 shop_id
ALTER TABLE `shopping_cart` ADD COLUMN `shop_id` bigint NOT NULL DEFAULT 1 COMMENT '所属店铺id' AFTER `id`;
ALTER TABLE `shopping_cart` ALTER COLUMN `shop_id` DROP DEFAULT;

ALTER TABLE `orders` ADD COLUMN `shop_id` bigint NOT NULL DEFAULT 1 COMMENT '所属店铺id' AFTER `id`;
ALTER TABLE `orders` ALTER COLUMN `shop_id` DROP DEFAULT;
