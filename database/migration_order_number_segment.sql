-- 订单号从"System.currentTimeMillis()转字符串"换成号段模式，见com.sky.ordernumber包。
-- 旧方案没有唯一性保证：两笔订单精确撞在同一毫秒会拿到完全相同的订单号，orders.number又被
-- payment()/微信支付回调的OrderMapper.getByNumber()当查找键用，真撞上会导致MyBatis对单对象
-- 返回类型的查询抛TooManyResultsException——是分析代码时发现的真实设计缺陷，不是假设的风险。
--
-- 已确认：跑这个脚本前查过sky_order_service.orders表现有20137行，number列20137个不同值，
-- 没有历史碰撞数据，可以安全加唯一索引；这台开发机上是这样，其它环境执行前建议先跑一遍
-- SELECT number, COUNT(*) FROM orders GROUP BY number HAVING COUNT(*) > 1 确认一下。
USE `sky_order_service`;

CREATE TABLE IF NOT EXISTS `order_number_segment` (
  `biz_key` varchar(32) NOT NULL COMMENT '业务标识，这个项目目前只有一个"orders"',
  `max_id` bigint NOT NULL DEFAULT 0 COMMENT '当前已分配到的最大id，号段分配靠这一列原子UPDATE递增',
  `step` int NOT NULL DEFAULT 1000 COMMENT '每次分配的号段步长',
  PRIMARY KEY (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单号号段分配表';

INSERT INTO `order_number_segment` (`biz_key`, `max_id`, `step`)
SELECT 'orders', 0, 1000
WHERE NOT EXISTS (SELECT 1 FROM `order_number_segment` WHERE `biz_key` = 'orders');

-- 顺手把碰撞风险彻底堵上：单靠"生成算法应该足够好"不够踏实，加唯一索引之后，
-- 即使将来生成逻辑又出bug，数据库层面也会在插入时直接报错拦下来，而不是让getByNumber
-- 在查询时才炸TooManyResultsException
ALTER TABLE `orders` ADD UNIQUE INDEX `uk_orders_number` (`number`);
