-- 手机号/身份证号改成应用层AES-256-GCM加密后落库（见 FieldCryptoUtil），密文比明文长得多，
-- 原来 varchar(11)/varchar(18) 装不下，先把列宽放大。
-- 注意：这条迁移只改列宽，不会重新加密表里已有的明文数据——旧数据会一直是明文，
-- 直到下次通过应用（而不是直接改库）更新这一行才会变成密文（FieldCryptoUtil.decrypt对读到的旧明文有兜底，不会读出乱码或报错）。

ALTER TABLE `employee` MODIFY COLUMN `phone` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '手机号（应用层加密存储）';
ALTER TABLE `employee` MODIFY COLUMN `id_number` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '身份证号（应用层加密存储）';
ALTER TABLE `address_book` MODIFY COLUMN `phone` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '手机号（应用层加密存储）';
