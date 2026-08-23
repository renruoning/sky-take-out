-- sky-invoice-service独立库：P4第二步拆分出来的发票服务，只有这一张表，
-- order_number/order_time/shop_name是申请时从sky-server快照下来的展示字段，不是JOIN出来的，
-- 只有sky-invoice-service这一个服务连这个库
CREATE DATABASE IF NOT EXISTS `sky_invoice_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `sky_invoice_service`;

DROP TABLE IF EXISTS `invoice`;
CREATE TABLE `invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '关联订单，一单只能开一张发票',
  `user_id` bigint NOT NULL COMMENT '申请人',
  `shop_id` bigint NOT NULL COMMENT '冗余存储店铺id',
  `title` varchar(100) NOT NULL COMMENT '发票抬头',
  `invoice_type` tinyint NOT NULL COMMENT '抬头类型 1个人 2单位',
  `tax_number` varchar(32) DEFAULT NULL COMMENT '纳税人识别号，单位抬头必填',
  `email` varchar(100) DEFAULT NULL COMMENT '接收邮箱，可选',
  `amount` decimal(10,2) NOT NULL COMMENT '冗余存订单金额',
  `order_number` varchar(50) DEFAULT NULL COMMENT '订单号快照（申请时从sky-server取，不再JOIN）',
  `order_time` datetime DEFAULT NULL COMMENT '下单时间快照',
  `shop_name` varchar(64) DEFAULT NULL COMMENT '店铺名快照',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_invoice_order_id` (`order_id`),
  KEY `idx_invoice_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票申请';
