-- sky-review-service独立库：P4第三步拆分出来的评价服务，只有这一张表，
-- order_number/user_name/user_avatar是提交评价时从sky-server快照下来的展示字段，不是JOIN出来的，
-- 只有sky-review-service这一个服务连这个库
CREATE DATABASE IF NOT EXISTS `sky_review_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `sky_review_service`;

DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
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
  KEY `idx_review_shop_id` (`shop_id`),
  -- pageQuery是 WHERE shop_id = ? ORDER BY create_time DESC，两列索引让排序也走索引，
  -- 不再Using filesort（见database/migration_review_shop_time_index.sql）
  KEY `idx_review_shop_time` (`shop_id`,`create_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单评价';
