-- sky-order-service独立数据库，从sky_take_out拆分出来（P4第五步）：orders/order_detail/shop三张表。
-- shop这次跟订单一起搬，不是单独的一步——见TODO.md的说明，shop表本身没有其它服务/领域直接依赖，
-- 只有跟order-service自己内部（InternalOrderController拼shopName）和员工鉴权（shop_id JWT claim，
-- 跟表在哪个物理库无关）有关系。

CREATE DATABASE IF NOT EXISTS `sky_order_service`;
USE `sky_order_service`;

DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop` (
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

DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
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

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
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
  `user_avatar` varchar(500) COLLATE utf8_bin DEFAULT NULL COMMENT '用户头像（下单时快照，供评价服务复用，不用再反查user表——这次新加的字段）',
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
