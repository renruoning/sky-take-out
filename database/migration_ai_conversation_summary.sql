-- AI客服历史裁剪/摘要：会话消息数超过AiChatServiceImpl.RECENT_KEEP_MESSAGES后，更早的消息不再原文
-- 发给OpenAI，改成滚动摘要（省token/费用，避免单个长会话的请求体无限增长）。
USE `sky_ai_service`;

ALTER TABLE `ai_conversation`
  ADD COLUMN `summary` text DEFAULT NULL COMMENT '更早历史的滚动摘要，为空表示还没长到需要裁剪' AFTER `title`,
  ADD COLUMN `summarized_through_message_id` bigint DEFAULT NULL COMMENT 'summary已经融合到了哪一条ai_message为止' AFTER `summary`;
