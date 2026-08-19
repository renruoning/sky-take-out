-- 给4个示例商户各建一个"老板"账号（本质就是绑定了shopId的员工账号，登录后台后天然只能看到自己店的数据）
USE `sky_take_out`;

INSERT INTO `employee` (shop_id, name, username, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user) VALUES
  (1, '蜀味阁老板', 'boss1', 'e10adc3949ba59abbe56e057f20f883e', '13800000011', '1', '110101199001010011', 1, NOW(), NOW(), 1, 1),
  (2, '江南小厨老板', 'boss2', 'e10adc3949ba59abbe56e057f20f883e', '13800000012', '1', '110101199001010012', 1, NOW(), NOW(), 1, 1),
  (3, '健康药房老板', 'boss3', 'e10adc3949ba59abbe56e057f20f883e', '13800000013', '0', '110101199001010013', 1, NOW(), NOW(), 1, 1),
  (4, '花语鲜花老板', 'boss4', 'e10adc3949ba59abbe56e057f20f883e', '13800000014', '0', '110101199001010014', 1, NOW(), NOW(), 1, 1);
