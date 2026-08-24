-- sky-ai-service独立库：P4第一步拆分出来的AI客服服务，只有这两张表，
-- 只有sky-ai-service这一个服务连这个库，不再跟sky_take_out主库共享
CREATE DATABASE IF NOT EXISTS `sky_ai_service` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `sky_ai_service`;

DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(50) DEFAULT NULL COMMENT '会话标题，取自第一条用户消息的前若干字',
  `summary` text DEFAULT NULL COMMENT '更早历史的滚动摘要，为空表示还没长到需要裁剪（见migration_ai_conversation_summary.sql）',
  `summarized_through_message_id` bigint DEFAULT NULL COMMENT 'summary已经融合到了哪一条ai_message为止',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服会话';

DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL COMMENT 'user 或 assistant',
  `content` text NOT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI客服消息记录';
