-- Seata Saga状态机的日志存储表：记录submitOrderSaga这个状态机每次执行到哪一步、成没成功、
-- 补偿有没有触发。跑在order-service自己的库（sky_order_service）里，因为状态机引擎是跟着
-- order-service这个进程（发起分布式事务的一方）跑的，不是跑在Seata Server（TC）那边。
-- 官方脚本：https://github.com/apache/incubator-seata/blob/v1.6.1/script/client/saga/db/mysql.sql
-- 表名前缀跟application.yml里seata.saga.state-machine.table-prefix=seata_保持一致，不要改前缀，
-- 否则状态机引擎会因为找不到这几张表而在下单时报错

USE sky_order_service;

CREATE TABLE IF NOT EXISTS `seata_state_machine_def`
(
    `id`               VARCHAR(32)  NOT NULL COMMENT 'id',
    `name`             VARCHAR(128) NOT NULL COMMENT 'name',
    `tenant_id`        VARCHAR(32)  NOT NULL COMMENT 'tenant id',
    `app_name`         VARCHAR(32)  NOT NULL COMMENT 'application name',
    `type`             VARCHAR(20)  COMMENT 'state language type',
    `comment_`         VARCHAR(255) COMMENT 'comment',
    `ver`              VARCHAR(16)  NOT NULL COMMENT 'version',
    `gmt_create`       DATETIME(3)  NOT NULL COMMENT 'create time',
    `status`           VARCHAR(2)   NOT NULL COMMENT 'status(AC:active|IN:inactive)',
    `content`          TEXT COMMENT 'content',
    `recover_strategy` VARCHAR(16) COMMENT 'transaction recover strategy(compensate|retry)',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `seata_state_machine_inst`
(
    `id`                  VARCHAR(128)            NOT NULL COMMENT 'id',
    `machine_id`          VARCHAR(32)             NOT NULL COMMENT 'state machine definition id',
    `tenant_id`           VARCHAR(32)             NOT NULL COMMENT 'tenant id',
    `parent_id`           VARCHAR(128) COMMENT 'parent id',
    `gmt_started`         DATETIME(3)             NOT NULL COMMENT 'start time',
    `business_key`        VARCHAR(48) COMMENT 'business key',
    `start_params`        TEXT COMMENT 'start parameters',
    `gmt_end`             DATETIME(3) COMMENT 'end time',
    `excep`               BLOB COMMENT 'exception',
    `end_params`          TEXT COMMENT 'end parameters',
    `status`              VARCHAR(2) COMMENT 'status(SU succeed|FA failed|UN unknown|SK skipped|RU running)',
    `compensation_status` VARCHAR(2) COMMENT 'compensation status(SU succeed|FA failed|UN unknown|SK skipped|RU running)',
    `is_running`          TINYINT(1) COMMENT 'is running(0 no|1 yes)',
    `gmt_updated`         DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `unikey_buz_tenant` (`business_key`, `tenant_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `seata_state_inst`
(
    `id`                       VARCHAR(48)  NOT NULL COMMENT 'id',
    `machine_inst_id`          VARCHAR(128) NOT NULL COMMENT 'state machine instance id',
    `name`                     VARCHAR(128) NOT NULL COMMENT 'state name',
    `type`                     VARCHAR(20)  COMMENT 'state type',
    `service_name`             VARCHAR(128) COMMENT 'service name',
    `service_method`           VARCHAR(128) COMMENT 'method name',
    `service_type`             VARCHAR(16) COMMENT 'service type',
    `business_key`             VARCHAR(48) COMMENT 'business key',
    `state_id_compensated_for` VARCHAR(50) COMMENT 'state compensated for',
    `state_id_retried_for`     VARCHAR(50) COMMENT 'state retried for',
    `gmt_started`              DATETIME(3)  NOT NULL COMMENT 'start time',
    `is_for_update`            TINYINT(1) COMMENT 'is service for update',
    `input_params`             TEXT COMMENT 'input parameters',
    `output_params`            TEXT COMMENT 'output parameters',
    `status`                   VARCHAR(2)   NOT NULL COMMENT 'status(SU succeed|FA failed|UN unknown|SK skipped|RU running)',
    `excep`                    BLOB COMMENT 'exception',
    `gmt_updated`              DATETIME(3) COMMENT 'update time',
    `gmt_end`                  DATETIME(3) COMMENT 'end time',
    PRIMARY KEY (`id`, `machine_inst_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
