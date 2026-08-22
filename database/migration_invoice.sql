-- 发票申请
USE `sky_take_out`;

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
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_invoice_order_id` (`order_id`),
  KEY `idx_invoice_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票申请';
