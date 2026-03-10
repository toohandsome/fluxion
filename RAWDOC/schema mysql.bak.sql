-- Fluxion phase 1 schema
-- Database: MySQL 8.x
-- Charset: utf8mb4

CREATE TABLE `flx_flow_def` (
  `id` BIGINT NOT NULL COMMENT '主键(雪花算法)',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `flow_code` VARCHAR(64) NOT NULL COMMENT '流程业务编码',
  `flow_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '流程描述',
  `category` VARCHAR(64) DEFAULT NULL COMMENT '分类目录',
  `active_version_id` BIGINT DEFAULT NULL COMMENT '当前生效版本ID',
  `latest_version_num` INT NOT NULL DEFAULT 0 COMMENT '当前最大版本号',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
  `delete_token` BIGINT NOT NULL DEFAULT 0 COMMENT '删除占位令牌, 未删除固定为0',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_flow_code_del` (`tenant_id`, `flow_code`, `delete_token`),
  KEY `idx_tenant_category_del` (`tenant_id`, `category`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表';

CREATE TABLE `flx_flow_draft` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `flow_def_id` BIGINT NOT NULL COMMENT '关联 flx_flow_def.id',
  `base_version_id` BIGINT DEFAULT NULL COMMENT '草稿基线版本ID, 未发布流程为空',
  `dsl_version` VARCHAR(32) NOT NULL DEFAULT '1.0' COMMENT '流程DSL版本号',
  `global_config` LONGTEXT COMMENT '全局配置(JSON)',
  `graph_json` LONGTEXT COMMENT '前端画布原始JSON',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '最近一次保存说明',
  `draft_revision` INT NOT NULL DEFAULT 0 COMMENT '草稿修订号, 用于并发控制',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_flow_draft` (`tenant_id`, `flow_def_id`),
  KEY `idx_tenant_base_version` (`tenant_id`, `base_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程草稿表';

CREATE TABLE `flx_flow_version` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `flow_def_id` BIGINT NOT NULL COMMENT '关联 flx_flow_def.id',
  `version_num` INT NOT NULL COMMENT '正式版本号(从1递增, 草稿不占号)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已发布, 2-已归档',
  `dsl_version` VARCHAR(32) NOT NULL DEFAULT '1.0' COMMENT '流程DSL版本号',
  `global_config` LONGTEXT COMMENT '全局配置(JSON)',
  `graph_json` LONGTEXT COMMENT '发布时固化的画布JSON快照',
  `model_json` LONGTEXT COMMENT '引擎执行模型JSON',
  `model_checksum` VARCHAR(128) DEFAULT NULL COMMENT '执行模型校验值',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '发布说明',
  `published_time` DATETIME(3) DEFAULT NULL COMMENT '发布时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-删除',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '发布人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_def_version` (`flow_def_id`, `version_num`),
  KEY `idx_tenant_def_status_ver` (`tenant_id`, `flow_def_id`, `status`, `version_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程版本快照表';

CREATE TABLE `flx_flow_instance` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `flow_def_id` BIGINT NOT NULL COMMENT '流程定义ID',
  `flow_version_id` BIGINT NOT NULL COMMENT '实际执行版本ID',
  `root_inst_id` BIGINT NOT NULL COMMENT '根流程实例ID, 主流程等于自身ID',
  `parent_inst_id` BIGINT DEFAULT NULL COMMENT '父流程实例ID',
  `flow_code` VARCHAR(64) NOT NULL COMMENT '流程编码快照',
  `flow_name` VARCHAR(128) NOT NULL COMMENT '流程名称快照',
  `flow_version_num` INT NOT NULL COMMENT '流程版本号快照',
  `business_key` VARCHAR(128) DEFAULT NULL COMMENT '业务关联键',
  `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
  `trigger_type` TINYINT NOT NULL COMMENT '触发方式: 1-API, 2-CRON, 3-MANUAL, 4-SUB_PROCESS',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '总耗时(毫秒)',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '触发人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_time` (`tenant_id`, `status`, `start_time`),
  KEY `idx_tenant_biz_time` (`tenant_id`, `business_key`, `create_time`),
  KEY `idx_tenant_def_start` (`tenant_id`, `flow_def_id`, `start_time`),
  KEY `idx_tenant_root_time` (`tenant_id`, `root_inst_id`, `create_time`),
  KEY `idx_tenant_parent` (`tenant_id`, `parent_inst_id`, `status`),
  KEY `idx_tenant_trigger` (`tenant_id`, `trigger_type`, `create_time`),
  KEY `idx_tenant_trace` (`tenant_id`, `trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

CREATE TABLE `flx_flow_instance_data` (
  `instance_id` BIGINT NOT NULL COMMENT '共享主键, 关联 flx_flow_instance.id',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `input_data` LONGTEXT COMMENT '触发输入快照(JSON)',
  `output_data` LONGTEXT COMMENT '最终输出快照(JSON)',
  `global_context` LONGTEXT COMMENT '全局上下文快照(JSON)',
  `error_msg` LONGTEXT COMMENT '流程级异常信息',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例大字段表';

CREATE TABLE `flx_node_execution` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `flow_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
  `parent_execution_id` BIGINT DEFAULT NULL COMMENT '父执行ID',
  `node_key` VARCHAR(64) NOT NULL COMMENT '节点内部标识',
  `node_name` VARCHAR(128) NOT NULL COMMENT '节点显示名称',
  `node_type` VARCHAR(64) NOT NULL COMMENT '节点类型',
  `scope_path` VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '执行域路径',
  `iteration_no` INT NOT NULL DEFAULT 0 COMMENT '循环迭代号, 0表示非循环',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消, 5-已跳过',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '累计重试次数',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '真实执行尝试次数, SKIPPED 固定为0',
  `timeout_ms` INT DEFAULT NULL COMMENT '节点超时时间快照(毫秒)',
  `executor_type` VARCHAR(32) DEFAULT NULL COMMENT '执行器类型',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '业务错误码/系统错误码',
  `skip_reason` VARCHAR(64) DEFAULT NULL COMMENT '跳过原因: BRANCH_NOT_MATCHED, ALL_UPSTREAM_PATHS_INVALIDATED',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_inst_time` (`tenant_id`, `flow_instance_id`, `start_time`),
  KEY `idx_tenant_parent_exec` (`tenant_id`, `flow_instance_id`, `parent_execution_id`),
  KEY `idx_tenant_status_time` (`tenant_id`, `status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行状态表(含跳过结论)';

CREATE TABLE `flx_node_execution_data` (
  `execution_id` BIGINT NOT NULL COMMENT '共享主键, 关联 flx_node_execution.id',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `input_data` LONGTEXT COMMENT '节点输入快照(JSON)',
  `output_data` LONGTEXT COMMENT '节点输出快照(JSON)',
  `error_log` LONGTEXT COMMENT '异常堆栈',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行大字段表';

CREATE TABLE `flx_node_execution_attempt` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `execution_id` BIGINT NOT NULL COMMENT '关联 flx_node_execution.id',
  `attempt_no` INT NOT NULL COMMENT '第几次尝试, 从1开始',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误摘要',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exec_attempt_no` (`execution_id`, `attempt_no`),
  KEY `idx_tenant_exec_attempt` (`tenant_id`, `execution_id`, `attempt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行尝试明细表(仅记录真实执行)';

CREATE TABLE `flx_http_endpoint` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `endpoint_code` VARCHAR(64) NOT NULL COMMENT '接口业务编码',
  `flow_def_id` BIGINT NOT NULL COMMENT '绑定流程定义ID',
  `version_policy` TINYINT NOT NULL DEFAULT 0 COMMENT '版本策略: 0-LATEST, 1-FIXED',
  `fixed_version_id` BIGINT DEFAULT NULL COMMENT '固定版本ID, FIXED时必填',
  `path` VARCHAR(255) NOT NULL COMMENT '请求路径',
  `method` VARCHAR(16) NOT NULL DEFAULT 'POST' COMMENT 'HTTP方法',
  `auth_type` TINYINT NOT NULL DEFAULT 0 COMMENT '认证方式: 0-开放, 1-AppKey, 2-BearerToken',
  `request_config` LONGTEXT COMMENT '请求提取/校验/业务键配置(JSON)',
  `response_type` VARCHAR(32) NOT NULL DEFAULT 'JSON' COMMENT '响应格式: 一期固定为JSON包装',
  `response_config` LONGTEXT COMMENT '响应data映射配置(JSON), 一期不改变外层响应包',
  `rate_limit_config` LONGTEXT COMMENT '接口限流配置(JSON)',
  `timeout_ms` INT DEFAULT NULL COMMENT '接口整体超时时间(毫秒)',
  `sync_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '0-异步, 1-同步',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-下线, 1-上线',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_endpoint_code` (`tenant_id`, `endpoint_code`),
  UNIQUE KEY `uk_tenant_path_method` (`tenant_id`, `path`, `method`),
  KEY `idx_tenant_flow_status` (`tenant_id`, `flow_def_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态HTTP接口发布表';

CREATE TABLE `flx_schedule_job` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `job_code` VARCHAR(64) NOT NULL COMMENT '任务业务编码',
  `job_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
  `flow_def_id` BIGINT NOT NULL COMMENT '触发流程定义ID',
  `version_policy` TINYINT NOT NULL DEFAULT 0 COMMENT '版本策略: 0-LATEST, 1-FIXED',
  `fixed_version_id` BIGINT DEFAULT NULL COMMENT '固定版本ID, FIXED时必填',
  `cron_expression` VARCHAR(64) NOT NULL COMMENT 'Quartz Cron表达式',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'Cron时区',
  `misfire_policy` TINYINT NOT NULL DEFAULT 1 COMMENT '错失策略: 1-立即补一次, 2-丢弃本次, 3-尽量追赶',
  `job_params` LONGTEXT COMMENT '任务静态参数(JSON)',
  `max_concurrency` INT NOT NULL DEFAULT 1 COMMENT '最大并发执行数',
  `reentry_policy` TINYINT NOT NULL DEFAULT 0 COMMENT '重入策略: 0-禁止重入, 1-允许并发',
  `timeout_ms` INT DEFAULT NULL COMMENT '单次触发等待流程进入终态的超时时间(毫秒)',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-暂停, 1-启用',
  `last_fire_time` DATETIME(3) DEFAULT NULL COMMENT '最近执行时间',
  `next_fire_time` DATETIME(3) DEFAULT NULL COMMENT '下次执行时间',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_job_code` (`tenant_id`, `job_code`),
  UNIQUE KEY `uk_tenant_job_name` (`tenant_id`, `job_name`),
  KEY `idx_tenant_status_fire` (`tenant_id`, `status`, `next_fire_time`),
  KEY `idx_tenant_flow_job_status` (`tenant_id`, `flow_def_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务配置表';

CREATE TABLE `flx_schedule_trigger_log` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `job_id` BIGINT NOT NULL COMMENT '关联 flx_schedule_job.id',
  `quartz_fire_instance_id` VARCHAR(128) DEFAULT NULL COMMENT 'Quartz fireInstanceId',
  `trigger_node_id` VARCHAR(128) DEFAULT NULL COMMENT '触发节点标识',
  `trigger_node_ip` VARCHAR(64) DEFAULT NULL COMMENT '触发节点IP',
  `trigger_status` TINYINT NOT NULL COMMENT '触发结果: 0-失败, 1-成功',
  `flow_instance_id` BIGINT DEFAULT NULL COMMENT '成功时关联流程实例ID',
  `error_message` TEXT COMMENT '触发失败原因',
  `scheduled_fire_time` DATETIME(3) NOT NULL COMMENT '理论触发时间',
  `trigger_time` DATETIME(3) NOT NULL COMMENT '实际开始触发时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '触发完成时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '触发耗时',
  `create_time` DATETIME(3) NOT NULL COMMENT '日志写入时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_job_trigger` (`tenant_id`, `job_id`, `trigger_time`),
  KEY `idx_tenant_schedule_time` (`tenant_id`, `scheduled_fire_time`),
  KEY `idx_tenant_flow_inst` (`tenant_id`, `flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务触发流水表';

CREATE TABLE `flx_resource` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `resource_code` VARCHAR(64) NOT NULL COMMENT '资源业务编码',
  `resource_name` VARCHAR(128) NOT NULL COMMENT '资源名称',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '资源类型: DB, HTTP, REDIS, OSS, CUSTOM',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '资源描述',
  `config_json` LONGTEXT COMMENT '非敏感配置(JSON)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
  `test_status` TINYINT NOT NULL DEFAULT 0 COMMENT '连通性状态: 0-未测试, 1-成功, 2-失败',
  `last_test_time` DATETIME(3) DEFAULT NULL COMMENT '最近测试时间',
  `last_test_message` VARCHAR(1000) DEFAULT NULL COMMENT '最近测试结果摘要',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `delete_token` BIGINT NOT NULL DEFAULT 0 COMMENT '删除占位令牌, 未删除固定为0',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_res_code_del` (`tenant_id`, `resource_code`, `delete_token`),
  KEY `idx_tenant_res_type_status` (`tenant_id`, `resource_type`, `status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源连接表';

CREATE TABLE `flx_resource_secret` (
  `resource_id` BIGINT NOT NULL COMMENT '共享主键, 关联 flx_resource.id',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `encrypt_alg` VARCHAR(32) NOT NULL DEFAULT 'AES_GCM' COMMENT '加密算法',
  `kek_key_id` VARCHAR(128) DEFAULT NULL COMMENT '主密钥标识',
  `secret_ciphertext` LONGTEXT NOT NULL COMMENT '敏感配置密文(JSON)',
  `secret_version` INT NOT NULL DEFAULT 1 COMMENT '密钥版本',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  `update_time` DATETIME(3) DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源密钥表';



CREATE TABLE `flx_operation_audit` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '租户ID',
  `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(128) DEFAULT NULL COMMENT '操作人名称',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '操作类型: CREATE, UPDATE, DELETE, PUBLISH, ENABLE, DISABLE',
  `target_type` VARCHAR(64) NOT NULL COMMENT '目标类型: FLOW, VERSION, ENDPOINT, JOB, RESOURCE',
  `target_id` BIGINT DEFAULT NULL COMMENT '目标ID',
  `target_code` VARCHAR(128) DEFAULT NULL COMMENT '目标业务编码',
  `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求ID',
  `request_ip` VARCHAR(64) DEFAULT NULL COMMENT '请求IP',
  `operation_content` LONGTEXT COMMENT '操作内容(JSON)',
  `result_status` TINYINT NOT NULL DEFAULT 1 COMMENT '结果: 0-失败, 1-成功',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因摘要',
  `create_time` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_target_time` (`tenant_id`, `target_type`, `target_id`, `create_time`),
  KEY `idx_tenant_operator_time` (`tenant_id`, `operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计表';
