-- 游标分页改造实测发现：keyset查询的tie-break条件（order_time < ? OR (order_time = ? AND id < ?)）
-- 用migration_orders_user_time_index.sql那个两列索引(user_id, order_time DESC)时，EXPLAIN显示走的是
-- idx_orders_user_id而不是它，且Extra带Using filesort——两列索引只覆盖了排序的第一个字段，
-- id这个tie-break字段不在索引里，MySQL没法只靠索引顺序满足"order_time DESC, id DESC"这个完整排序。
-- 补上id列扩成三列索引后EXPLAIN确认filesort消失（range/Using index condition）。
-- 两列版本直接升级成三列版本，不用同时维护两个索引——三列版本同样能服务原来那些只按
-- user_id/order_time查询的场景（最左前缀），不会让别的查询变慢。
USE `sky_order_service`;

ALTER TABLE `orders`
  DROP INDEX `idx_orders_user_time`,
  ADD INDEX `idx_orders_user_time` (`user_id`, `order_time` DESC, `id` DESC);
