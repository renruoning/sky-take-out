CREATE DATABASE  IF NOT EXISTS `sky_take_out` ;
USE `sky_take_out`;

-- shop 已经不在这个库里了：P4第五步把订单（连同店铺）拆成了独立的sky-order-service服务，
-- 表连同数据整体搬到了独立的sky_order_service库（见database/sky_order_service.sql和
-- database/migration_split_order_service_db.sql）。shop这次没有单独一步搬，是跟orders/order_detail
-- 一起走的——shop本身不引用订单/商品，之所以没有更早独立拆分，是因为它跟"下单归属哪个店铺"这条线
-- 绑得比跟"商品目录"更紧（InternalOrderController需要拼shopName），一直等订单也要拆的这一步才顺路一起搬。

-- review 也已经不在这个库里了：P4第三步把评价拆成了独立的sky-review-service服务，
-- 表连同数据整体搬到了独立的sky_review_service库（见database/sky_review_service.sql和
-- database/migration_split_review_service_db.sql），新库里多了order_number/user_name/user_avatar
-- 三个快照字段，替代了原来对orders/user表的JOIN。

DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `phone` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '手机号（应用层加密存储）',
  `province_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_address_book_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='地址簿';

-- category / dish / dish_flavor / setmeal / setmeal_dish 已经不在这个库里了：
-- P4第四步把商品（分类/菜品/口味/套餐/套餐菜品关系）拆成了独立的sky-product-service服务，
-- 表连同数据整体搬到了独立的sky_product_service库（见database/sky_product_service.sql和
-- database/migration_split_product_service_db.sql），这五张表本来就不引用orders/user，
-- 是干净的原样搬迁，不需要像发票/评价那样额外回填快照字段。
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint DEFAULT NULL COMMENT '所属店铺id，null表示平台超管',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '用户名',
  `password` varchar(64) COLLATE utf8_bin NOT NULL COMMENT '密码',
  `phone` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '手机号（应用层加密存储）',
  `sex` varchar(2) COLLATE utf8_bin NOT NULL COMMENT '性别',
  `id_number` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '身份证号（应用层加密存储）',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='员工信息';

INSERT INTO `employee` VALUES (1,1,'管理员','admin','e10adc3949ba59abbe56e057f20f883e','13812312312','1','110101199001010047',1,'2022-02-15 15:51:20','2022-02-17 09:16:20',10,1);
INSERT INTO `employee` VALUES (2,NULL,'平台超管','platform','e10adc3949ba59abbe56e057f20f883e','13900000000','1','110101199001010099',1,NOW(),NOW(),1,1);
-- 4个示例商户各自的"老板"账号，密码都是123456
INSERT INTO `employee` VALUES (5,1,'蜀味阁老板','boss1','e10adc3949ba59abbe56e057f20f883e','13800000011','1','110101199001010011',1,NOW(),NOW(),1,1);
INSERT INTO `employee` VALUES (6,2,'江南小厨老板','boss2','e10adc3949ba59abbe56e057f20f883e','13800000012','1','110101199001010012',1,NOW(),NOW(),1,1);
INSERT INTO `employee` VALUES (7,3,'健康药房老板','boss3','e10adc3949ba59abbe56e057f20f883e','13800000013','0','110101199001010013',1,NOW(),NOW(),1,1);
INSERT INTO `employee` VALUES (8,4,'花语鲜花老板','boss4','e10adc3949ba59abbe56e057f20f883e','13800000014','0','110101199001010014',1,NOW(),NOW(),1,1);

-- order_detail / orders 已经不在这个库里了：P4第五步把订单拆成了独立的sky-order-service服务（跟shop
-- 一起搬，见上面的说明），表连同数据整体搬到了独立的sky_order_service库（见database/sky_order_service.sql
-- 和database/migration_split_order_service_db.sql）。新库里orders表多了一个user_avatar快照列
-- （之前没有，这次新加），跟已有的user_name一样是下单时快照，供评价服务的OrderSummaryVO.userAvatar用，
-- 不用再为这一个字段单独反查user表。

-- ai_conversation / ai_message 已经不在这个库里了：P4第一步把AI客服拆成了独立的sky-ai-service服务，
-- 这两张表连同数据整体搬到了独立的sky_ai_service库（见database/sky_ai_service.sql和
-- database/migration_split_ai_service_db.sql），不是死代码遗留、是故意的边界收敛。

-- invoice 也已经不在这个库里了：P4第二步把发票拆成了独立的sky-invoice-service服务，
-- 表连同数据整体搬到了独立的sky_invoice_service库（见database/sky_invoice_service.sql和
-- database/migration_split_invoice_service_db.sql），新库里多了order_number/order_time/shop_name
-- 三个快照字段，替代了原来对orders/shop表的JOIN。

-- 购物车已经迁移到Redis（见 TODO.md P1，ShoppingCartServiceImpl 用 Redis Hash 存储），
-- 不再需要 shopping_cart 这张MySQL表，新装库不会创建它。

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '用户名（账号密码登录）',
  `password` varchar(64) COLLATE utf8_bin DEFAULT NULL COMMENT '密码（MD5加密）',
  `openid` varchar(45) COLLATE utf8_bin DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8_bin DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) COLLATE utf8_bin DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_username` (`username`),
  UNIQUE KEY `idx_user_openid` (`openid`),
  KEY `idx_user_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='用户信息';

INSERT INTO `user` (`username`, `password`, `create_time`) VALUES ('user1', 'e10adc3949ba59abbe56e057f20f883e', NOW());

DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `level` varchar(10) NOT NULL COMMENT '日志级别 WARN/ERROR',
  `logger` varchar(255) DEFAULT NULL COMMENT '所在类',
  `thread` varchar(100) DEFAULT NULL COMMENT '线程名',
  `message` varchar(2000) DEFAULT NULL COMMENT '日志内容',
  `exception` text COMMENT '异常堆栈',
  `create_time` datetime NOT NULL COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统日志（WARN/ERROR，由日志框架异步写入）';