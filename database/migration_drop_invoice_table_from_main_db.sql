-- 确认sky_invoice_service库里数据已经搬完（见migration_split_invoice_service_db.sql）之后执行，
-- 把主库里的旧表清掉——跟shopping_cart/ai_conversation/ai_message下线用的是同一个模式
DROP TABLE IF EXISTS `sky_take_out`.`invoice`;
