-- 确认sky_product_service库里数据已经搬完（见migration_split_product_service_db.sql）之后执行，
-- 把主库里的旧表清掉——跟之前几次下线用的是同一个模式。先删有外键方向依赖的子表(dish_flavor/setmeal_dish)
DROP TABLE IF EXISTS `sky_take_out`.`dish_flavor`;
DROP TABLE IF EXISTS `sky_take_out`.`setmeal_dish`;
DROP TABLE IF EXISTS `sky_take_out`.`dish`;
DROP TABLE IF EXISTS `sky_take_out`.`setmeal`;
DROP TABLE IF EXISTS `sky_take_out`.`category`;
