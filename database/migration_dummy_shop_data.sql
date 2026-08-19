-- 补充商户2/3/4的分类+菜品 dummy data，并修复商户3在早期营业类型测试中被改乱的名字/类型
USE `sky_take_out`;

-- 商户3 之前被业务类型冷却测试改成了"花卉"，改回种子数据里原本设定的"医药"
UPDATE `shop`
SET name = '健康药房（示例商户3）',
    business_type = 2,
    secondary_business_type = NULL,
    business_type_updated_at = NULL
WHERE id = 3;

-- 清理商户2上残留的禁用测试分类
DELETE FROM `category` WHERE shop_id = 2 AND name = 'Shop2Category';

-- ---------------- 商户2：江南小厨（餐饮 + 蔬果） ----------------
INSERT INTO `category` (shop_id, type, name, sort, status, create_time, update_time, create_user, update_user) VALUES
  (2, 1, '江南小炒', 1, 1, NOW(), NOW(), 1, 1),
  (2, 1, '精致点心', 2, 1, NOW(), NOW(), 1, 1),
  (2, 1, '时令蔬菜', 3, 1, NOW(), NOW(), 1, 1);

INSERT INTO `dish` (shop_id, name, category_id, price, image, description, status, need_prescription, create_time, update_time, create_user, update_user)
SELECT 2, v.name, c.id, v.price, '', v.description, 1, 0, NOW(), NOW(), 1, 1
FROM (
  SELECT '江南小炒' AS cat, '西湖醋鱼' AS name, 38.00 AS price, '原料：草鱼，糖醋汁' AS description
  UNION ALL SELECT '江南小炒', '龙井虾仁', 58.00, '原料：虾仁，龙井茶'
  UNION ALL SELECT '江南小炒', '干煸四季豆', 22.00, '原料：四季豆，肉末'
  UNION ALL SELECT '精致点心', '小笼包', 18.00, '原料：猪肉，汤汁'
  UNION ALL SELECT '精致点心', '生煎包', 16.00, '原料：猪肉，面粉'
  UNION ALL SELECT '时令蔬菜', '新鲜草莓', 25.00, '当季直采，甜度高'
  UNION ALL SELECT '时令蔬菜', '有机小黄瓜', 12.00, '农家自种，脆嫩爽口'
) v
JOIN `category` c ON c.shop_id = 2 AND c.name = v.cat;

-- ---------------- 商户3：健康药房（医药） ----------------
INSERT INTO `category` (shop_id, type, name, sort, status, create_time, update_time, create_user, update_user) VALUES
  (3, 1, '感冒用药', 1, 1, NOW(), NOW(), 1, 1),
  (3, 1, '医疗器械', 2, 1, NOW(), NOW(), 1, 1),
  (3, 1, '处方药专区', 3, 1, NOW(), NOW(), 1, 1);

INSERT INTO `dish` (shop_id, name, category_id, price, image, description, status, need_prescription, create_time, update_time, create_user, update_user)
SELECT 3, v.name, c.id, v.price, '', v.description, 1, v.rx, NOW(), NOW(), 1, 1
FROM (
  SELECT '感冒用药' AS cat, '999感冒灵颗粒' AS name, 18.00 AS price, '缓解感冒引起的头痛、发热' AS description, 0 AS rx
  UNION ALL SELECT '感冒用药', '布洛芬缓释胶囊', 15.00, '解热镇痛', 0
  UNION ALL SELECT '医疗器械', '电子体温计', 35.00, '精准测温，30秒速测', 0
  UNION ALL SELECT '医疗器械', '医用外科口罩(50只)', 20.00, '独立包装，防护三层结构', 0
  UNION ALL SELECT '处方药专区', '阿莫西林胶囊', 12.00, '需凭处方购买，请遵医嘱', 1
  UNION ALL SELECT '处方药专区', '消炎药膏', 22.00, '需凭处方购买，请遵医嘱', 1
) v
JOIN `category` c ON c.shop_id = 3 AND c.name = v.cat;

-- ---------------- 商户4：花语鲜花（花卉） ----------------
INSERT INTO `category` (shop_id, type, name, sort, status, create_time, update_time, create_user, update_user) VALUES
  (4, 1, '鲜花花束', 1, 1, NOW(), NOW(), 1, 1),
  (4, 1, '绿植盆栽', 2, 1, NOW(), NOW(), 1, 1),
  (4, 1, '节日礼盒', 3, 1, NOW(), NOW(), 1, 1);

INSERT INTO `dish` (shop_id, name, category_id, price, image, description, status, need_prescription, create_time, update_time, create_user, update_user)
SELECT 4, v.name, c.id, v.price, '', v.description, 1, 0, NOW(), NOW(), 1, 1
FROM (
  SELECT '鲜花花束' AS cat, '19朵红玫瑰花束' AS name, 199.00 AS price, '精选进口玫瑰，搭配满天星' AS description
  UNION ALL SELECT '鲜花花束', '向日葵花束', 129.00, '寓意阳光积极，9朵装'
  UNION ALL SELECT '绿植盆栽', '多肉植物组合', 59.00, '3-5株组合装，易打理'
  UNION ALL SELECT '绿植盆栽', '绿萝盆栽', 39.00, '净化空气，办公室好选择'
  UNION ALL SELECT '节日礼盒', '情人节礼盒', 299.00, '玫瑰花束+巧克力+贺卡'
  UNION ALL SELECT '节日礼盒', '生日祝福花篮', 168.00, '混搭鲜花，附祝福卡片'
) v
JOIN `category` c ON c.shop_id = 4 AND c.name = v.cat;
