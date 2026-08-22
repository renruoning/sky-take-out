-- 确认sky_ai_service库里数据已经搬完（见migration_split_ai_service_db.sql）之后执行，
-- 把主库里的旧表清掉——跟之前shopping_cart下线用的是同一个模式
DROP TABLE IF EXISTS `sky_take_out`.`ai_message`;
DROP TABLE IF EXISTS `sky_take_out`.`ai_conversation`;
