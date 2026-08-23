-- 确认sky_review_service库里数据已经搬完（见migration_split_review_service_db.sql）之后执行，
-- 把主库里的旧表清掉——跟之前几次下线用的是同一个模式
DROP TABLE IF EXISTS `sky_take_out`.`review`;
