-- P4第三步：把review从sky_take_out主库物理拆到独立的sky_review_service库。
-- 这是唯一一次跨库JOIN——趁两边数据还在同一个MySQL实例上，把order_number/user_name/user_avatar
-- 三个快照字段一次性回填好，之后sky-server和sky-review-service就不再共享任何表。
-- 执行完用migration_drop_review_table_from_main_db.sql清理主库里的旧表

CREATE DATABASE IF NOT EXISTS `sky_review_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sky_review_service`.`review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '关联订单，一单只能评一次',
  `shop_id` bigint NOT NULL COMMENT '冗余存储店铺id',
  `user_id` bigint NOT NULL COMMENT '评价人',
  `order_number` varchar(50) DEFAULT NULL COMMENT '订单号快照（提交时从sky-server取，不再JOIN）',
  `user_name` varchar(64) DEFAULT NULL COMMENT '评价人姓名快照',
  `user_avatar` varchar(255) DEFAULT NULL COMMENT '评价人头像快照',
  `rating` tinyint NOT NULL COMMENT '1-5星',
  `content` varchar(500) DEFAULT NULL,
  `images` varchar(1000) DEFAULT NULL COMMENT '图片url，逗号分隔',
  `reply` varchar(500) DEFAULT NULL COMMENT '商家回复',
  `reply_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_review_order_id` (`order_id`),
  KEY `idx_review_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单评价';

INSERT INTO `sky_review_service`.`review`
    (id, order_id, shop_id, user_id, order_number, user_name, user_avatar, rating, content, images, reply, reply_time, create_time)
SELECT
    r.id, r.order_id, r.shop_id, r.user_id, o.number, u.name, u.avatar,
    r.rating, r.content, r.images, r.reply, r.reply_time, r.create_time
FROM `sky_take_out`.`review` r
LEFT JOIN `sky_take_out`.`orders` o ON o.id = r.order_id
LEFT JOIN `sky_take_out`.`user` u ON u.id = r.user_id;

-- AUTO_INCREMENT对齐，避免新库里下一条insert的自增id跟原库的已有最大值撞在一起
SELECT IFNULL(MAX(id), 0) + 1 INTO @next_review_id FROM `sky_take_out`.`review`;
SET @sql = CONCAT('ALTER TABLE `sky_review_service`.`review` AUTO_INCREMENT = ', @next_review_id);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
