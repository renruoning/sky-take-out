-- P4第五步：把orders/order_detail/shop从sky_take_out主库物理拆到独立的sky_order_service库。
-- shop这次跟订单一起搬（见sky_order_service.sql开头的说明）。orders表这次新增了user_avatar快照列
-- （之前没有，这次新加——评价服务的OrderSummaryVO.userAvatar需要，不想为这一个字段单独反查user表），
-- 所以orders不能像order_detail/shop那样直接INSERT...SELECT *，需要LEFT JOIN老库里的user表回填一次，
-- 之后新库里都是走orders表自己的user_avatar列，不再需要这个JOIN。
-- 执行完用migration_drop_order_tables_from_main_db.sql清理主库里的旧表。

CREATE DATABASE IF NOT EXISTS `sky_order_service` DEFAULT CHARACTER SET utf8mb3 COLLATE utf8_bin;

CREATE TABLE IF NOT EXISTS `sky_order_service`.`shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '店铺名称',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `business_type` int NOT NULL DEFAULT '1' COMMENT '主营业类型 1=餐饮 2=医药 3=蔬果 4=花卉',
  `secondary_business_type` int DEFAULT NULL COMMENT '副营业类型，取值同business_type，可为空',
  `business_type_updated_at` datetime DEFAULT NULL COMMENT '主/副营业类型最近一次被修改的时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺（商户）';

CREATE TABLE IF NOT EXISTS `sky_order_service`.`order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '名字',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`),
  KEY `idx_order_detail_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单明细表';

CREATE TABLE IF NOT EXISTS `sky_order_service`.`orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint NOT NULL COMMENT '所属店铺id',
  `number` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) COLLATE utf8_bin DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '用户名称（下单时快照）',
  `user_avatar` varchar(500) COLLATE utf8_bin DEFAULT NULL COMMENT '用户头像（下单时快照，这次新加的字段）',
  `consignee` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int DEFAULT NULL COMMENT '打包费',
  `tableware_number` int DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态  1按餐量提供  0选择具体数量',
  PRIMARY KEY (`id`),
  KEY `idx_orders_status_order_time` (`status`,`order_time`),
  KEY `idx_orders_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单表';

INSERT INTO `sky_order_service`.`shop` SELECT * FROM `sky_take_out`.`shop`;
INSERT INTO `sky_order_service`.`order_detail` SELECT * FROM `sky_take_out`.`order_detail`;

-- orders表多了一列，不能直接SELECT *：LEFT JOIN老库自己的user表把历史订单的user_avatar一次性回填
INSERT INTO `sky_order_service`.`orders`
  (id, shop_id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status,
   amount, remark, phone, address, user_name, user_avatar, consignee, cancel_reason, rejection_reason, cancel_time,
   estimated_delivery_time, delivery_status, delivery_time, pack_amount, tableware_number, tableware_status)
SELECT o.id, o.shop_id, o.number, o.status, o.user_id, o.address_book_id, o.order_time, o.checkout_time, o.pay_method, o.pay_status,
       o.amount, o.remark, o.phone, o.address, o.user_name, u.avatar, o.consignee, o.cancel_reason, o.rejection_reason, o.cancel_time,
       o.estimated_delivery_time, o.delivery_status, o.delivery_time, o.pack_amount, o.tableware_number, o.tableware_status
FROM `sky_take_out`.`orders` o
LEFT JOIN `sky_take_out`.`user` u ON u.id = o.user_id;

-- AUTO_INCREMENT对齐，避免新库里下一条insert的自增id跟原库的已有最大值撞在一起
SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`shop`;
SET @sql = CONCAT('ALTER TABLE `sky_order_service`.`shop` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`order_detail`;
SET @sql = CONCAT('ALTER TABLE `sky_order_service`.`order_detail` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_id FROM `sky_take_out`.`orders`;
SET @sql = CONCAT('ALTER TABLE `sky_order_service`.`orders` AUTO_INCREMENT = ', @next_id);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
