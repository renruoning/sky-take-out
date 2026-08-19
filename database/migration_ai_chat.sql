-- AI客服聊天 迁移脚本
USE `sky_take_out`;

DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(50) DEFAULT NULL COMMENT '会话标题，取自第一条用户消息的前若干字',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服会话';

DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'user 或 assistant',
  `content` text NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服消息记录';
