-- 确认sky_order_service库里数据已经搬完（见migration_split_order_service_db.sql）之后执行，
-- 把主库里的旧表清掉——跟之前几次下线用的是同一个模式。先删子表(order_detail)，再删orders/shop
DROP TABLE IF EXISTS `sky_take_out`.`order_detail`;
DROP TABLE IF EXISTS `sky_take_out`.`orders`;
DROP TABLE IF EXISTS `sky_take_out`.`shop`;
