-- P4第二步：把invoice从sky_take_out主库物理拆到独立的sky_invoice_service库。
-- 这是唯一一次跨库JOIN——这时候两边数据还在同一个MySQL实例上，借这次机会把order_number/order_time/shop_name
-- 三个快照字段一次性回填好，之后sky-server和sky-invoice-service就不再共享任何表。
-- 执行完用migration_drop_invoice_table_from_main_db.sql清理主库里的旧表

CREATE DATABASE IF NOT EXISTS `sky_invoice_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sky_invoice_service`.`invoice` (
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

INSERT INTO `sky_invoice_service`.`invoice`
    (id, order_id, user_id, shop_id, title, invoice_type, tax_number, email, amount, order_number, order_time, shop_name, create_time)
SELECT
    i.id, i.order_id, i.user_id, i.shop_id, i.title, i.invoice_type, i.tax_number, i.email, i.amount,
    o.number, o.order_time, s.name, i.create_time
FROM `sky_take_out`.`invoice` i
LEFT JOIN `sky_take_out`.`orders` o ON o.id = i.order_id
LEFT JOIN `sky_take_out`.`shop` s ON s.id = i.shop_id;

-- AUTO_INCREMENT对齐，避免新库里下一条insert的自增id跟原库的已有最大值撞在一起
SELECT IFNULL(MAX(id), 0) + 1 INTO @next_invoice_id FROM `sky_take_out`.`invoice`;
SET @sql = CONCAT('ALTER TABLE `sky_invoice_service`.`invoice` AUTO_INCREMENT = ', @next_invoice_id);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
