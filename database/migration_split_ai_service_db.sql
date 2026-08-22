-- P4第一步：把ai_conversation/ai_message从sky_take_out主库物理拆到独立的sky_ai_service库，
-- 把已有的真实数据原样搬过去（不是从空库重新开始），执行完用migration_drop_ai_tables_from_main_db.sql清理主库里的旧表

CREATE DATABASE IF NOT EXISTS `sky_ai_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sky_ai_service`.`ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(50) DEFAULT NULL COMMENT '会话标题，取自第一条用户消息的前若干字',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服会话';

CREATE TABLE IF NOT EXISTS `sky_ai_service`.`ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'user 或 assistant',
  `content` text NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服消息记录';

-- 同一个MySQL实例内跨库insert-select，把sky_take_out里现有的真实会话/消息数据原样搬过去
INSERT INTO `sky_ai_service`.`ai_conversation` (id, user_id, title, create_time, update_time)
SELECT id, user_id, title, create_time, update_time FROM `sky_take_out`.`ai_conversation`;

INSERT INTO `sky_ai_service`.`ai_message` (id, conversation_id, role, content, create_time)
SELECT id, conversation_id, role, content, create_time FROM `sky_take_out`.`ai_message`;

-- AUTO_INCREMENT对齐，避免新库里下一条insert的自增id跟原库的已有最大值撞在一起
-- （ALTER TABLE ... AUTO_INCREMENT = 不接受子查询，用动态SQL拼出实际数值再执行）
SELECT IFNULL(MAX(id), 0) + 1 INTO @next_conv_id FROM `sky_take_out`.`ai_conversation`;
SET @sql = CONCAT('ALTER TABLE `sky_ai_service`.`ai_conversation` AUTO_INCREMENT = ', @next_conv_id);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT IFNULL(MAX(id), 0) + 1 INTO @next_msg_id FROM `sky_take_out`.`ai_message`;
SET @sql = CONCAT('ALTER TABLE `sky_ai_service`.`ai_message` AUTO_INCREMENT = ', @next_msg_id);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
