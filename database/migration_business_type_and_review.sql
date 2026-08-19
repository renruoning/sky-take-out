-- 多品类（店铺主/副营业类型） + 用户评价 迁移脚本
USE `sky_take_out`;

-- 1. shop 表加主/副营业类型 + 最近修改时间
ALTER TABLE `shop` ADD COLUMN `business_type` int NOT NULL DEFAULT 1 COMMENT '主营业类型 1=餐饮 2=医药 3=蔬果 4=花卉' AFTER `status`;
ALTER TABLE `shop` ADD COLUMN `secondary_business_type` int NULL COMMENT '副营业类型，取值同business_type，可为空' AFTER `business_type`;
ALTER TABLE `shop` ADD COLUMN `business_type_updated_at` datetime NULL COMMENT '主/副营业类型最近一次被修改的时间' AFTER `secondary_business_type`;

-- business_type_updated_at 留空：冷却期从第一次真实修改营业类型时才开始计算，不是从建店时间算起
UPDATE `shop` SET business_type = 1 WHERE id = 1;
UPDATE `shop` SET business_type = 1, secondary_business_type = 3 WHERE id = 2;

INSERT INTO `shop` (name, address, phone, status, business_type, secondary_business_type, business_type_updated_at, create_time, update_time, create_user, update_user) VALUES
  ('健康药房（示例商户3）', '广州市天河区示例路3号', '13800000003', 1, 2, NULL, NULL, NOW(), NOW(), 1, 1),
  ('花语鲜花（示例商户4）', '深圳市南山区示例路4号', '13800000004', 1, 4, NULL, NULL, NOW(), NOW(), 1, 1);

-- 1b. dish 表加“是否处方药”标记（仅医药类目店铺的商品会用到，其他业务线忽略即可）
ALTER TABLE `dish` ADD COLUMN `need_prescription` int NOT NULL DEFAULT 0 COMMENT '是否处方药 0否 1是' AFTER `status`;

-- 2. 新增 review 表
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '关联订单，一单只能评一次',
  `shop_id` bigint NOT NULL COMMENT '冗余存储店铺id',
  `user_id` bigint NOT NULL,
  `rating` tinyint NOT NULL COMMENT '1-5星',
  `content` varchar(500) DEFAULT NULL,
  `images` varchar(1000) DEFAULT NULL COMMENT '图片url，逗号分隔',
  `reply` varchar(500) DEFAULT NULL COMMENT '商家回复',
  `reply_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_review_order_id` (`order_id`),
  KEY `idx_review_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价';
