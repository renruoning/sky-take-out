-- review/list分页优化：ReviewMapper.pageQuery是 WHERE shop_id = ? [AND rating = ?] ORDER BY create_time DESC，
-- 原来只有单列idx_review_shop_id，排序没索引可用。跟historyOrders当时是同一个模式的问题，见
-- database/migration_orders_user_time_index.sql的说明，这次直接照搬同一套打法（两列索引）。
--
-- 这次只做索引优化这一步，没有跟进游标分页——评价数据量还小，offset分页的COUNT(*)开销暂时不是瓶颈，
-- 等真的量大了再引入游标分页（到时候索引要跟orders表那次一样从两列升级成三列(shop_id, create_time DESC, id DESC)，
-- 见migration_orders_user_time_id_index.sql踩过的那个坑）。见TODO.md"review/list索引优化"这条。
--
-- 保留原来的单列idx_review_shop_id，不删——照抄orders表的先例（idx_orders_user_id也是保留没删），
-- 两个索引不冲突，避免节外生枝改动跟这次优化无关的东西。
USE `sky_review_service`;

ALTER TABLE `review`
  ADD INDEX `idx_review_shop_time` (`shop_id`, `create_time` DESC);
