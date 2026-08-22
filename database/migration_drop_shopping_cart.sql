-- 购物车已经迁移到Redis（见 TODO.md P1），MySQL里的 shopping_cart 表不再被任何代码使用，删掉。
DROP TABLE IF EXISTS `shopping_cart`;
