-- 下单失败后的库存补偿兜底：扣库存成功但建单失败时，反向补偿调用（Feign）本身也可能失败，
-- 原来失败后只记一条ERROR日志、没有任何重试或持久化记录，进程崩溃或多次失败后这笔库存就
-- 永久"虚减"了。这张表配合OrderServiceImpl.persistPendingCompensation/StockCompensationTask：
-- 补偿之前先把意图落盘（独立事务），定时任务扫出还没完成的记录持续重试，直到成功或人工介入。
-- 在sky_order_service库执行。
USE `sky_order_service`;

CREATE TABLE IF NOT EXISTS `stock_compensation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stock_items` text NOT NULL COMMENT '需要恢复的库存明细（JSON序列化的List<StockChangeItemDTO>）',
  `status` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0待补偿 1已完成',
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_create_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下单失败后的库存补偿记录，配合定时任务兜底重试';
