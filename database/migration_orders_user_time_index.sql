-- historyOrders分页查询优化：pageQuery是 WHERE user_id = ? ORDER BY order_time DESC，
-- 原来只有单列idx_orders_user_id，排序没索引可用，EXPLAIN确认Extra是Using filesort。
-- 已经现场加过一次同样的索引验证过有效（filesort消失），当时测完撤回了，这次正式走迁移脚本落地。
-- 见REPORT.md/TODO.md里"大表分页查询瓶颈"这条的完整诊断过程。
USE `sky_order_service`;

ALTER TABLE `orders`
  ADD INDEX `idx_orders_user_time` (`user_id`, `order_time` DESC);
