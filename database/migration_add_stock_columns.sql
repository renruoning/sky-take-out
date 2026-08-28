-- 给菜品/套餐加库存字段，配合order-service的库存扣减/恢复功能。
-- 在sky_product_service库执行（dish/setmeal已经拆到这个库里）。
USE `sky_product_service`;

ALTER TABLE `dish` ADD COLUMN `stock` int NOT NULL DEFAULT '1000' COMMENT '库存数量' AFTER `need_prescription`;
ALTER TABLE `setmeal` ADD COLUMN `stock` int NOT NULL DEFAULT '1000' COMMENT '库存数量' AFTER `image`;

-- 已有数据的库存统一先设成1000（DEFAULT '1000'只对之后新插入的行生效，已存在的行要显式UPDATE一遍）
UPDATE `dish` SET `stock` = 1000;
UPDATE `setmeal` SET `stock` = 1000;
