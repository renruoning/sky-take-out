-- C端用户改为账号密码登录（暂时和微信解绑，微信相关字段/逻辑保留）
USE `sky_take_out`;

ALTER TABLE `user`
  ADD COLUMN `username` varchar(32) DEFAULT NULL COMMENT '用户名（账号密码登录）' AFTER `id`,
  ADD COLUMN `password` varchar(64) DEFAULT NULL COMMENT '密码（MD5加密）' AFTER `username`;

ALTER TABLE `user`
  ADD UNIQUE KEY `idx_user_username` (`username`);

-- 测试账号 user1 / 123456
INSERT INTO `user` (`username`, `password`, `create_time`)
VALUES ('user1', 'e10adc3949ba59abbe56e057f20f883e', NOW());
