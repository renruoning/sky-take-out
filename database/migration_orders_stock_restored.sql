-- 给orders加stock_restored标记位，配合库存恢复的幂等改造（OrderMapper.markStockRestored）：
-- 取消/拒单/超时自动取消三条路径在真正调用product-service恢复库存之前，先对这一列做一次
-- CAS（UPDATE ... SET stock_restored=1 WHERE id=? AND stock_restored=0），0行说明已经被恢复过，
-- 直接跳过，不重复调用——这样即使分布式锁在极端情况下失效导致两条路径都进了临界区，
-- 也不会把同一笔订单的库存恢复两次。在sky_order_service库执行（orders已经拆到这个库里）。
USE `sky_order_service`;

ALTER TABLE `orders` ADD COLUMN `stock_restored` tinyint(1) NOT NULL DEFAULT '0' COMMENT '库存是否已恢复 0否 1是（取消/拒单/超时取消时置1，防止重复恢复）' AFTER `tableware_status`;
