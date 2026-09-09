-- 手写库存补偿（persistPendingCompensation/StockCompensationTask）已经被Seata Saga状态机
-- （submitOrderSaga，见sky-order-service/src/main/resources/statelang/submit_order.json）替换掉，
-- OrderServiceImpl不再写这张表，StockCompensationTask这个定时任务也已经删除。
--
-- 已执行：正常下单、库存不足拒单、建单失败自动回滚库存三个场景都在新的Seata Saga链路下验证通过后
-- 跑的这个脚本。本地dev库里这张表实际上一直没被创建过（DROP TABLE IF EXISTS是空操作），
-- 但代码依赖已经清理干净，脚本保留作为其它环境（如果哪个环境真建过这张表）的清理记录。

USE `sky_order_service`;
DROP TABLE IF EXISTS `stock_compensation_log`;
