-- Fluxion phase 1 schema for PostgreSQL 14+
-- Encoding: UTF8

-- 流程定义表
CREATE TABLE flx_flow_def (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    flow_code VARCHAR(64) NOT NULL,
    flow_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    category VARCHAR(64) DEFAULT NULL,
    active_version_id BIGINT DEFAULT NULL,
    latest_version_num INTEGER NOT NULL DEFAULT 0,
    revision INTEGER NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_flow_def PRIMARY KEY (id),
    CONSTRAINT uk_flx_flow_def_tenant_flow_code_del UNIQUE (tenant_id, flow_code, delete_token)
);

COMMENT ON TABLE flx_flow_def IS '流程定义表';
COMMENT ON COLUMN flx_flow_def.id IS '主键(雪花算法)';
COMMENT ON COLUMN flx_flow_def.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_flow_def.flow_code IS '流程业务编码';
COMMENT ON COLUMN flx_flow_def.flow_name IS '流程名称';
COMMENT ON COLUMN flx_flow_def.description IS '流程描述';
COMMENT ON COLUMN flx_flow_def.category IS '分类目录';
COMMENT ON COLUMN flx_flow_def.active_version_id IS '当前生效版本ID';
COMMENT ON COLUMN flx_flow_def.latest_version_num IS '当前最大版本号';
COMMENT ON COLUMN flx_flow_def.revision IS '乐观锁版本';
COMMENT ON COLUMN flx_flow_def.is_deleted IS '逻辑删除: 0-正常, 1-删除';
COMMENT ON COLUMN flx_flow_def.delete_token IS '删除占位令牌, 未删除固定为0';
COMMENT ON COLUMN flx_flow_def.create_time IS '创建时间';
COMMENT ON COLUMN flx_flow_def.create_by IS '创建人';
COMMENT ON COLUMN flx_flow_def.update_time IS '更新时间';
COMMENT ON COLUMN flx_flow_def.update_by IS '更新人';

CREATE INDEX idx_flx_flow_def_tenant_category_del ON flx_flow_def(tenant_id, category, is_deleted);

-- 流程草稿表
CREATE TABLE flx_flow_draft (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    flow_def_id BIGINT NOT NULL,
    base_version_id BIGINT DEFAULT NULL,
    dsl_version VARCHAR(32) NOT NULL DEFAULT '1.0',
    global_config TEXT,
    graph_json TEXT,
    remark VARCHAR(255) DEFAULT NULL,
    draft_revision INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_flow_draft PRIMARY KEY (id),
    CONSTRAINT uk_flx_flow_draft_tenant_flow_def UNIQUE (tenant_id, flow_def_id)
);

COMMENT ON TABLE flx_flow_draft IS '流程草稿表';
COMMENT ON COLUMN flx_flow_draft.id IS '主键';
COMMENT ON COLUMN flx_flow_draft.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_flow_draft.flow_def_id IS '关联 flx_flow_def.id';
COMMENT ON COLUMN flx_flow_draft.base_version_id IS '草稿基线版本ID, 未发布流程为空';
COMMENT ON COLUMN flx_flow_draft.dsl_version IS '流程DSL版本号';
COMMENT ON COLUMN flx_flow_draft.global_config IS '全局配置(JSON)';
COMMENT ON COLUMN flx_flow_draft.graph_json IS '前端画布原始JSON';
COMMENT ON COLUMN flx_flow_draft.remark IS '最近一次保存说明';
COMMENT ON COLUMN flx_flow_draft.draft_revision IS '草稿修订号, 用于并发控制';
COMMENT ON COLUMN flx_flow_draft.create_time IS '创建时间';
COMMENT ON COLUMN flx_flow_draft.create_by IS '创建人';
COMMENT ON COLUMN flx_flow_draft.update_time IS '更新时间';
COMMENT ON COLUMN flx_flow_draft.update_by IS '更新人';

CREATE INDEX idx_flx_flow_draft_tenant_base_version ON flx_flow_draft(tenant_id, base_version_id);

-- 流程版本快照表
CREATE TABLE flx_flow_version (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    flow_def_id BIGINT NOT NULL,
    version_num INTEGER NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    dsl_version VARCHAR(32) NOT NULL DEFAULT '1.0',
    global_config TEXT,
    graph_json TEXT,
    model_json TEXT,
    model_checksum VARCHAR(128) DEFAULT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    published_time TIMESTAMP(3) DEFAULT NULL,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_flow_version PRIMARY KEY (id),
    CONSTRAINT uk_flx_flow_version_def_version UNIQUE (flow_def_id, version_num)
);

COMMENT ON TABLE flx_flow_version IS '流程版本快照表';
COMMENT ON COLUMN flx_flow_version.id IS '主键';
COMMENT ON COLUMN flx_flow_version.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_flow_version.flow_def_id IS '关联 flx_flow_def.id';
COMMENT ON COLUMN flx_flow_version.version_num IS '正式版本号(从1递增, 草稿不占号)';
COMMENT ON COLUMN flx_flow_version.status IS '状态: 1-已发布, 2-已归档';
COMMENT ON COLUMN flx_flow_version.dsl_version IS '流程DSL版本号';
COMMENT ON COLUMN flx_flow_version.global_config IS '全局配置(JSON)';
COMMENT ON COLUMN flx_flow_version.graph_json IS '发布时固化的画布JSON快照';
COMMENT ON COLUMN flx_flow_version.model_json IS '引擎执行模型JSON';
COMMENT ON COLUMN flx_flow_version.model_checksum IS '执行模型校验值';
COMMENT ON COLUMN flx_flow_version.remark IS '发布说明';
COMMENT ON COLUMN flx_flow_version.published_time IS '发布时间';
COMMENT ON COLUMN flx_flow_version.is_deleted IS '逻辑删除: 0-正常, 1-删除';
COMMENT ON COLUMN flx_flow_version.create_time IS '创建时间';
COMMENT ON COLUMN flx_flow_version.create_by IS '发布人';
COMMENT ON COLUMN flx_flow_version.update_time IS '更新时间';
COMMENT ON COLUMN flx_flow_version.update_by IS '更新人';

CREATE INDEX idx_flx_flow_version_tenant_def_status_ver ON flx_flow_version(tenant_id, flow_def_id, status, version_num);

-- 流程实例表
CREATE TABLE flx_flow_instance (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    flow_def_id BIGINT NOT NULL,
    flow_version_id BIGINT NOT NULL,
    root_inst_id BIGINT NOT NULL,
    parent_inst_id BIGINT DEFAULT NULL,
    flow_code VARCHAR(64) NOT NULL,
    flow_name VARCHAR(128) NOT NULL,
    flow_version_num INTEGER NOT NULL,
    business_key VARCHAR(128) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    trigger_type SMALLINT NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    start_time TIMESTAMP(3) DEFAULT NULL,
    end_time TIMESTAMP(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_flow_instance PRIMARY KEY (id)
);

COMMENT ON TABLE flx_flow_instance IS '流程实例表';
COMMENT ON COLUMN flx_flow_instance.id IS '主键';
COMMENT ON COLUMN flx_flow_instance.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_flow_instance.flow_def_id IS '流程定义ID';
COMMENT ON COLUMN flx_flow_instance.flow_version_id IS '实际执行版本ID';
COMMENT ON COLUMN flx_flow_instance.root_inst_id IS '根流程实例ID, 主流程等于自身ID';
COMMENT ON COLUMN flx_flow_instance.parent_inst_id IS '父流程实例ID';
COMMENT ON COLUMN flx_flow_instance.flow_code IS '流程编码快照';
COMMENT ON COLUMN flx_flow_instance.flow_name IS '流程名称快照';
COMMENT ON COLUMN flx_flow_instance.flow_version_num IS '流程版本号快照';
COMMENT ON COLUMN flx_flow_instance.business_key IS '业务关联键';
COMMENT ON COLUMN flx_flow_instance.trace_id IS '链路追踪ID';
COMMENT ON COLUMN flx_flow_instance.trigger_type IS '触发方式: 1-API, 2-CRON, 3-MANUAL, 4-SUB_PROCESS';
COMMENT ON COLUMN flx_flow_instance.status IS '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消';
COMMENT ON COLUMN flx_flow_instance.start_time IS '开始时间';
COMMENT ON COLUMN flx_flow_instance.end_time IS '结束时间';
COMMENT ON COLUMN flx_flow_instance.duration_ms IS '总耗时(毫秒)';
COMMENT ON COLUMN flx_flow_instance.create_time IS '创建时间';
COMMENT ON COLUMN flx_flow_instance.create_by IS '触发人';
COMMENT ON COLUMN flx_flow_instance.update_time IS '更新时间';
COMMENT ON COLUMN flx_flow_instance.update_by IS '更新人';

CREATE INDEX idx_flx_flow_instance_tenant_status_time ON flx_flow_instance(tenant_id, status, start_time);
CREATE INDEX idx_flx_flow_instance_tenant_biz_time ON flx_flow_instance(tenant_id, business_key, create_time);
CREATE INDEX idx_flx_flow_instance_tenant_def_start ON flx_flow_instance(tenant_id, flow_def_id, start_time);
CREATE INDEX idx_flx_flow_instance_tenant_root_time ON flx_flow_instance(tenant_id, root_inst_id, create_time);
CREATE INDEX idx_flx_flow_instance_tenant_parent ON flx_flow_instance(tenant_id, parent_inst_id, status);
CREATE INDEX idx_flx_flow_instance_tenant_trigger ON flx_flow_instance(tenant_id, trigger_type, create_time);
CREATE INDEX idx_flx_flow_instance_tenant_trace ON flx_flow_instance(tenant_id, trace_id);

-- 流程实例大字段表
CREATE TABLE flx_flow_instance_data (
    instance_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    input_data TEXT,
    output_data TEXT,
    global_context TEXT,
    error_msg TEXT,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_flx_flow_instance_data PRIMARY KEY (instance_id)
);

COMMENT ON TABLE flx_flow_instance_data IS '流程实例大字段表';
COMMENT ON COLUMN flx_flow_instance_data.instance_id IS '共享主键, 关联 flx_flow_instance.id';
COMMENT ON COLUMN flx_flow_instance_data.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_flow_instance_data.input_data IS '触发输入快照(JSON)';
COMMENT ON COLUMN flx_flow_instance_data.output_data IS '最终输出快照(JSON)';
COMMENT ON COLUMN flx_flow_instance_data.global_context IS '全局上下文快照(JSON)';
COMMENT ON COLUMN flx_flow_instance_data.error_msg IS '流程级异常信息';
COMMENT ON COLUMN flx_flow_instance_data.create_time IS '创建时间';
COMMENT ON COLUMN flx_flow_instance_data.update_time IS '更新时间';

-- 节点执行状态表(含跳过结论)
CREATE TABLE flx_node_execution (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    flow_instance_id BIGINT NOT NULL,
    parent_execution_id BIGINT DEFAULT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    scope_path VARCHAR(512) NOT NULL DEFAULT '/',
    iteration_no INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    start_time TIMESTAMP(3) DEFAULT NULL,
    end_time TIMESTAMP(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    timeout_ms INTEGER DEFAULT NULL,
    executor_type VARCHAR(32) DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    skip_reason VARCHAR(64) DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_node_execution PRIMARY KEY (id)
);

COMMENT ON TABLE flx_node_execution IS '节点执行状态表(含跳过结论)';
COMMENT ON COLUMN flx_node_execution.id IS '主键';
COMMENT ON COLUMN flx_node_execution.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_node_execution.flow_instance_id IS '流程实例ID';
COMMENT ON COLUMN flx_node_execution.parent_execution_id IS '父执行ID';
COMMENT ON COLUMN flx_node_execution.node_key IS '节点内部标识';
COMMENT ON COLUMN flx_node_execution.node_name IS '节点显示名称';
COMMENT ON COLUMN flx_node_execution.node_type IS '节点类型';
COMMENT ON COLUMN flx_node_execution.scope_path IS '执行域路径';
COMMENT ON COLUMN flx_node_execution.iteration_no IS '循环迭代号, 0表示非循环';
COMMENT ON COLUMN flx_node_execution.status IS '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消, 5-已跳过';
COMMENT ON COLUMN flx_node_execution.start_time IS '开始时间';
COMMENT ON COLUMN flx_node_execution.end_time IS '结束时间';
COMMENT ON COLUMN flx_node_execution.duration_ms IS '耗时(毫秒)';
COMMENT ON COLUMN flx_node_execution.retry_count IS '累计重试次数';
COMMENT ON COLUMN flx_node_execution.attempt_count IS '真实执行尝试次数, SKIPPED 固定为0';
COMMENT ON COLUMN flx_node_execution.timeout_ms IS '节点超时时间快照(毫秒)';
COMMENT ON COLUMN flx_node_execution.executor_type IS '执行器类型';
COMMENT ON COLUMN flx_node_execution.error_code IS '业务错误码/系统错误码';
COMMENT ON COLUMN flx_node_execution.skip_reason IS '跳过原因: BRANCH_NOT_MATCHED, ALL_UPSTREAM_PATHS_INVALIDATED';
COMMENT ON COLUMN flx_node_execution.create_time IS '创建时间';
COMMENT ON COLUMN flx_node_execution.create_by IS '创建人';
COMMENT ON COLUMN flx_node_execution.update_time IS '更新时间';
COMMENT ON COLUMN flx_node_execution.update_by IS '更新人';

CREATE INDEX idx_flx_node_execution_tenant_inst_time ON flx_node_execution(tenant_id, flow_instance_id, start_time);
CREATE INDEX idx_flx_node_execution_tenant_parent_exec ON flx_node_execution(tenant_id, flow_instance_id, parent_execution_id);
CREATE INDEX idx_flx_node_execution_tenant_status_time ON flx_node_execution(tenant_id, status, start_time);

-- 节点执行大字段表
CREATE TABLE flx_node_execution_data (
    execution_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    input_data TEXT,
    output_data TEXT,
    error_log TEXT,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_flx_node_execution_data PRIMARY KEY (execution_id)
);

COMMENT ON TABLE flx_node_execution_data IS '节点执行大字段表';
COMMENT ON COLUMN flx_node_execution_data.execution_id IS '共享主键, 关联 flx_node_execution.id';
COMMENT ON COLUMN flx_node_execution_data.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_node_execution_data.input_data IS '节点输入快照(JSON)';
COMMENT ON COLUMN flx_node_execution_data.output_data IS '节点输出快照(JSON)';
COMMENT ON COLUMN flx_node_execution_data.error_log IS '异常堆栈';
COMMENT ON COLUMN flx_node_execution_data.create_time IS '创建时间';
COMMENT ON COLUMN flx_node_execution_data.update_time IS '更新时间';

-- 节点执行尝试明细表(仅记录真实执行)
CREATE TABLE flx_node_execution_attempt (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    execution_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    start_time TIMESTAMP(3) DEFAULT NULL,
    end_time TIMESTAMP(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_flx_node_execution_attempt PRIMARY KEY (id),
    CONSTRAINT uk_flx_node_execution_attempt_exec_attempt UNIQUE (execution_id, attempt_no)
);

COMMENT ON TABLE flx_node_execution_attempt IS '节点执行尝试明细表(仅记录真实执行)';
COMMENT ON COLUMN flx_node_execution_attempt.id IS '主键';
COMMENT ON COLUMN flx_node_execution_attempt.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_node_execution_attempt.execution_id IS '关联 flx_node_execution.id';
COMMENT ON COLUMN flx_node_execution_attempt.attempt_no IS '第几次尝试, 从1开始';
COMMENT ON COLUMN flx_node_execution_attempt.status IS '状态: 0-已创建, 1-运行中, 2-成功, 3-失败, 4-已取消';
COMMENT ON COLUMN flx_node_execution_attempt.start_time IS '开始时间';
COMMENT ON COLUMN flx_node_execution_attempt.end_time IS '结束时间';
COMMENT ON COLUMN flx_node_execution_attempt.duration_ms IS '耗时(毫秒)';
COMMENT ON COLUMN flx_node_execution_attempt.error_code IS '错误码';
COMMENT ON COLUMN flx_node_execution_attempt.error_message IS '错误摘要';
COMMENT ON COLUMN flx_node_execution_attempt.create_time IS '创建时间';

CREATE INDEX idx_flx_node_execution_attempt_tenant_exec_attempt ON flx_node_execution_attempt(tenant_id, execution_id, attempt_no);

-- 动态HTTP接口发布表
CREATE TABLE flx_http_endpoint (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    endpoint_code VARCHAR(64) NOT NULL,
    flow_def_id BIGINT NOT NULL,
    version_policy SMALLINT NOT NULL DEFAULT 0,
    fixed_version_id BIGINT DEFAULT NULL,
    path VARCHAR(255) NOT NULL,
    method VARCHAR(16) NOT NULL DEFAULT 'POST',
    auth_type SMALLINT NOT NULL DEFAULT 0,
    auth_credential_id BIGINT DEFAULT NULL,
    request_config TEXT,
    response_type VARCHAR(32) NOT NULL DEFAULT 'JSON',
    response_config TEXT,
    rate_limit_config TEXT,
    timeout_ms INTEGER DEFAULT NULL,
    sync_mode SMALLINT NOT NULL DEFAULT 1,
    status SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_http_endpoint PRIMARY KEY (id),
    CONSTRAINT uk_flx_http_endpoint_tenant_code UNIQUE (tenant_id, endpoint_code),
    CONSTRAINT uk_flx_http_endpoint_tenant_path_method UNIQUE (tenant_id, path, method)
);

COMMENT ON TABLE flx_http_endpoint IS '动态HTTP接口发布表';
COMMENT ON COLUMN flx_http_endpoint.id IS '主键';
COMMENT ON COLUMN flx_http_endpoint.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_http_endpoint.endpoint_code IS '接口业务编码';
COMMENT ON COLUMN flx_http_endpoint.flow_def_id IS '绑定流程定义ID';
COMMENT ON COLUMN flx_http_endpoint.version_policy IS '版本策略: 0-LATEST, 1-FIXED';
COMMENT ON COLUMN flx_http_endpoint.fixed_version_id IS '固定版本ID, FIXED时必填';
COMMENT ON COLUMN flx_http_endpoint.path IS '请求路径';
COMMENT ON COLUMN flx_http_endpoint.method IS 'HTTP方法';
COMMENT ON COLUMN flx_http_endpoint.auth_type IS '认证方式: 0-开放, 1-AppKey, 2-BearerToken,3-BasicAuth';
COMMENT ON COLUMN flx_http_endpoint.request_config IS '请求提取/校验/业务键配置(JSON)';
COMMENT ON COLUMN flx_http_endpoint.response_type IS '响应格式: 一期固定为JSON包装';
COMMENT ON COLUMN flx_http_endpoint.response_config IS '响应data映射配置(JSON), 一期不改变外层响应包';
COMMENT ON COLUMN flx_http_endpoint.rate_limit_config IS '接口限流配置(JSON)';
COMMENT ON COLUMN flx_http_endpoint.timeout_ms IS '接口整体超时时间(毫秒)';
COMMENT ON COLUMN flx_http_endpoint.sync_mode IS '0-异步, 1-同步';
COMMENT ON COLUMN flx_http_endpoint.status IS '0-下线, 1-上线';
COMMENT ON COLUMN flx_http_endpoint.create_time IS '创建时间';
COMMENT ON COLUMN flx_http_endpoint.create_by IS '创建人';
COMMENT ON COLUMN flx_http_endpoint.update_time IS '更新时间';
COMMENT ON COLUMN flx_http_endpoint.update_by IS '更新人';

COMMENT ON COLUMN flx_http_endpoint.auth_credential_id IS '认证凭证ID, OPEN时为空';

CREATE INDEX idx_flx_http_endpoint_tenant_flow_status ON flx_http_endpoint(tenant_id, flow_def_id, status);
CREATE INDEX idx_flx_http_endpoint_tenant_auth_cred ON flx_http_endpoint(tenant_id, auth_credential_id);

-- 定时任务配置表
CREATE TABLE flx_schedule_job (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    job_code VARCHAR(64) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    flow_def_id BIGINT NOT NULL,
    version_policy SMALLINT NOT NULL DEFAULT 0,
    fixed_version_id BIGINT DEFAULT NULL,
    cron_expression VARCHAR(64) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    misfire_policy SMALLINT NOT NULL DEFAULT 1,
    catch_up_config TEXT,
    job_params TEXT,
    max_concurrency INTEGER NOT NULL DEFAULT 1,
    reentry_policy SMALLINT NOT NULL DEFAULT 0,
    wait_timeout_ms INTEGER DEFAULT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    last_fire_time TIMESTAMP(3) DEFAULT NULL,
    next_fire_time TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_schedule_job PRIMARY KEY (id),
    CONSTRAINT uk_flx_schedule_job_tenant_code UNIQUE (tenant_id, job_code),
    CONSTRAINT uk_flx_schedule_job_tenant_name UNIQUE (tenant_id, job_name)
);

COMMENT ON TABLE flx_schedule_job IS '定时任务配置表';
COMMENT ON COLUMN flx_schedule_job.id IS '主键';
COMMENT ON COLUMN flx_schedule_job.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_schedule_job.job_code IS '任务业务编码';
COMMENT ON COLUMN flx_schedule_job.job_name IS '任务名称';
COMMENT ON COLUMN flx_schedule_job.flow_def_id IS '触发流程定义ID';
COMMENT ON COLUMN flx_schedule_job.version_policy IS '版本策略: 0-LATEST, 1-FIXED';
COMMENT ON COLUMN flx_schedule_job.fixed_version_id IS '固定版本ID, FIXED时必填';
COMMENT ON COLUMN flx_schedule_job.cron_expression IS 'Quartz Cron表达式';
COMMENT ON COLUMN flx_schedule_job.timezone IS 'Cron时区';
COMMENT ON COLUMN flx_schedule_job.misfire_policy IS '错失策略: 1-立即补一次, 2-丢弃本次, 3-有界补跑';
COMMENT ON COLUMN flx_schedule_job.catch_up_config IS '补跑边界配置(JSON), 如最近窗口、最大补跑次数、顺序';
COMMENT ON COLUMN flx_schedule_job.job_params IS '任务静态参数(JSON)';
COMMENT ON COLUMN flx_schedule_job.max_concurrency IS '最大并发执行数';
COMMENT ON COLUMN flx_schedule_job.reentry_policy IS '重入策略: 0-禁止重入, 1-允许并发';
COMMENT ON COLUMN flx_schedule_job.wait_timeout_ms IS '调度侧等待流程实例进入终态的最长时间(毫秒)';
COMMENT ON COLUMN flx_schedule_job.status IS '状态: 0-暂停, 1-启用';
COMMENT ON COLUMN flx_schedule_job.last_fire_time IS '最近执行时间';
COMMENT ON COLUMN flx_schedule_job.next_fire_time IS '下次执行时间';
COMMENT ON COLUMN flx_schedule_job.create_time IS '创建时间';
COMMENT ON COLUMN flx_schedule_job.create_by IS '创建人';
COMMENT ON COLUMN flx_schedule_job.update_time IS '更新时间';
COMMENT ON COLUMN flx_schedule_job.update_by IS '更新人';

CREATE INDEX idx_flx_schedule_job_tenant_status_fire ON flx_schedule_job(tenant_id, status, next_fire_time);
CREATE INDEX idx_flx_schedule_job_tenant_flow_status ON flx_schedule_job(tenant_id, flow_def_id, status);

-- 定时任务触发流水表
CREATE TABLE flx_schedule_trigger_log (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    job_id BIGINT NOT NULL,
    quartz_fire_instance_id VARCHAR(128) DEFAULT NULL,
    trigger_node_id VARCHAR(128) DEFAULT NULL,
    trigger_node_ip VARCHAR(64) DEFAULT NULL,
    fire_kind SMALLINT NOT NULL DEFAULT 0,
    summary_status SMALLINT NOT NULL DEFAULT 0,
    dispatch_status SMALLINT NOT NULL DEFAULT 0,
    wait_status SMALLINT NOT NULL DEFAULT 0,
    instance_status_snapshot SMALLINT NOT NULL DEFAULT 0,
    flow_instance_id BIGINT DEFAULT NULL,
    error_message TEXT,
    scheduled_fire_time TIMESTAMP(3) NOT NULL,
    trigger_time TIMESTAMP(3) NOT NULL,
    finish_time TIMESTAMP(3) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_flx_schedule_trigger_log PRIMARY KEY (id)
);

COMMENT ON TABLE flx_schedule_trigger_log IS '定时任务触发流水表';
COMMENT ON COLUMN flx_schedule_trigger_log.id IS '主键';
COMMENT ON COLUMN flx_schedule_trigger_log.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_schedule_trigger_log.job_id IS '关联 flx_schedule_job.id';
COMMENT ON COLUMN flx_schedule_trigger_log.quartz_fire_instance_id IS 'Quartz fireInstanceId';
COMMENT ON COLUMN flx_schedule_trigger_log.trigger_node_id IS '触发节点标识';
COMMENT ON COLUMN flx_schedule_trigger_log.trigger_node_ip IS '触发节点IP';
COMMENT ON COLUMN flx_schedule_trigger_log.fire_kind IS '触发种类: 0-正常触发, 1-补跑触发';
COMMENT ON COLUMN flx_schedule_trigger_log.summary_status IS '列表汇总状态: 0-已受理, 1-并发拒绝, 2-等待超时, 3-实例成功, 4-实例失败';
COMMENT ON COLUMN flx_schedule_trigger_log.dispatch_status IS '调度投递结果: 0-已受理, 1-并发拒绝, 2-版本不存在, 3-投递失败';
COMMENT ON COLUMN flx_schedule_trigger_log.wait_status IS '等待结果: 0-未等待, 1-已完成, 2-等待超时';
COMMENT ON COLUMN flx_schedule_trigger_log.instance_status_snapshot IS '实例状态快照: 0-未知, 1-运行中, 2-成功, 3-失败, 4-已取消';
COMMENT ON COLUMN flx_schedule_trigger_log.flow_instance_id IS '成功时关联流程实例ID';
COMMENT ON COLUMN flx_schedule_trigger_log.error_message IS '失败、拒绝或等待超时原因';
COMMENT ON COLUMN flx_schedule_trigger_log.scheduled_fire_time IS '理论触发时间';
COMMENT ON COLUMN flx_schedule_trigger_log.trigger_time IS '实际开始触发时间';
COMMENT ON COLUMN flx_schedule_trigger_log.finish_time IS '触发完成时间';
COMMENT ON COLUMN flx_schedule_trigger_log.duration_ms IS '触发耗时';
COMMENT ON COLUMN flx_schedule_trigger_log.create_time IS '日志写入时间';

CREATE INDEX idx_flx_schedule_trigger_log_tenant_job_trigger ON flx_schedule_trigger_log(tenant_id, job_id, trigger_time);
CREATE INDEX idx_flx_schedule_trigger_log_tenant_schedule_time ON flx_schedule_trigger_log(tenant_id, scheduled_fire_time);
CREATE INDEX idx_flx_schedule_trigger_log_tenant_flow_inst ON flx_schedule_trigger_log(tenant_id, flow_instance_id);

-- 资源连接表
CREATE TABLE flx_resource (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    resource_code VARCHAR(64) NOT NULL,
    resource_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    config_json TEXT,
    status SMALLINT NOT NULL DEFAULT 1,
    test_status SMALLINT NOT NULL DEFAULT 0,
    last_test_time TIMESTAMP(3) DEFAULT NULL,
    last_test_message VARCHAR(1000) DEFAULT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_resource PRIMARY KEY (id),
    CONSTRAINT uk_flx_resource_tenant_code_del UNIQUE (tenant_id, resource_code, delete_token)
);

COMMENT ON TABLE flx_resource IS '资源连接表';
COMMENT ON COLUMN flx_resource.id IS '主键';
COMMENT ON COLUMN flx_resource.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_resource.resource_code IS '资源业务编码';
COMMENT ON COLUMN flx_resource.resource_name IS '资源名称';
COMMENT ON COLUMN flx_resource.resource_type IS '资源类型: DB, HTTP, REDIS, OSS, CUSTOM';
COMMENT ON COLUMN flx_resource.description IS '资源描述';
COMMENT ON COLUMN flx_resource.config_json IS '非敏感配置(JSON)';
COMMENT ON COLUMN flx_resource.status IS '状态: 0-禁用, 1-启用';
COMMENT ON COLUMN flx_resource.test_status IS '连通性状态: 0-未测试, 1-成功, 2-失败';
COMMENT ON COLUMN flx_resource.last_test_time IS '最近测试时间';
COMMENT ON COLUMN flx_resource.last_test_message IS '最近测试结果摘要';
COMMENT ON COLUMN flx_resource.revision IS '乐观锁版本';
COMMENT ON COLUMN flx_resource.is_deleted IS '逻辑删除';
COMMENT ON COLUMN flx_resource.delete_token IS '删除占位令牌, 未删除固定为0';
COMMENT ON COLUMN flx_resource.create_time IS '创建时间';
COMMENT ON COLUMN flx_resource.create_by IS '创建人';
COMMENT ON COLUMN flx_resource.update_time IS '更新时间';
COMMENT ON COLUMN flx_resource.update_by IS '更新人';

CREATE INDEX idx_flx_resource_tenant_type_status ON flx_resource(tenant_id, resource_type, status, is_deleted);

-- 资源密钥表
CREATE TABLE flx_resource_secret (
    resource_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    encrypt_alg VARCHAR(32) NOT NULL DEFAULT 'AES_GCM',
    kek_key_id VARCHAR(128) DEFAULT NULL,
    secret_ciphertext TEXT NOT NULL,
    secret_version INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_flx_resource_secret PRIMARY KEY (resource_id)
);

COMMENT ON TABLE flx_resource_secret IS '资源密钥表';
COMMENT ON COLUMN flx_resource_secret.resource_id IS '共享主键, 关联 flx_resource.id';
COMMENT ON COLUMN flx_resource_secret.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_resource_secret.encrypt_alg IS '加密算法';
COMMENT ON COLUMN flx_resource_secret.kek_key_id IS '主密钥标识';
COMMENT ON COLUMN flx_resource_secret.secret_ciphertext IS '敏感配置密文(JSON)';
COMMENT ON COLUMN flx_resource_secret.secret_version IS '密钥版本';
COMMENT ON COLUMN flx_resource_secret.create_time IS '创建时间';
COMMENT ON COLUMN flx_resource_secret.update_time IS '更新时间';

-- 认证凭证表
CREATE TABLE flx_auth_credential (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    credential_code VARCHAR(64) NOT NULL,
    credential_name VARCHAR(128) NOT NULL,
    auth_type SMALLINT NOT NULL DEFAULT 3,
    config_json TEXT,
    description VARCHAR(500) DEFAULT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    revision INTEGER NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    CONSTRAINT pk_flx_auth_credential PRIMARY KEY (id),
    CONSTRAINT uk_flx_auth_credential_tenant_code_del UNIQUE (tenant_id, credential_code, delete_token)
);

COMMENT ON TABLE flx_auth_credential IS '认证凭证表';
COMMENT ON COLUMN flx_auth_credential.id IS '主键';
COMMENT ON COLUMN flx_auth_credential.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_auth_credential.credential_code IS '凭证业务编码';
COMMENT ON COLUMN flx_auth_credential.credential_name IS '凭证名称';
COMMENT ON COLUMN flx_auth_credential.auth_type IS '认证类型: 0-OPEN, 1-APP_KEY, 2-BEARER_TOKEN, 3-BASIC_AUTH（一期仅支持 BASIC_AUTH）';
COMMENT ON COLUMN flx_auth_credential.config_json IS '非敏感配置(JSON)';
COMMENT ON COLUMN flx_auth_credential.description IS '凭证描述';
COMMENT ON COLUMN flx_auth_credential.status IS '状态: 0-禁用, 1-启用';
COMMENT ON COLUMN flx_auth_credential.revision IS '乐观锁版本';
COMMENT ON COLUMN flx_auth_credential.is_deleted IS '逻辑删除';
COMMENT ON COLUMN flx_auth_credential.delete_token IS '删除占位令牌, 未删除固定为0';
COMMENT ON COLUMN flx_auth_credential.create_time IS '创建时间';
COMMENT ON COLUMN flx_auth_credential.create_by IS '创建人';
COMMENT ON COLUMN flx_auth_credential.update_time IS '更新时间';
COMMENT ON COLUMN flx_auth_credential.update_by IS '更新人';

CREATE INDEX idx_flx_auth_credential_tenant_type_status ON flx_auth_credential(tenant_id, auth_type, status, is_deleted);

-- 认证凭证密钥表
CREATE TABLE flx_auth_credential_secret (
    credential_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    encrypt_alg VARCHAR(32) NOT NULL DEFAULT 'AES_GCM',
    kek_key_id VARCHAR(128) DEFAULT NULL,
    secret_ciphertext TEXT NOT NULL,
    secret_version INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_flx_auth_credential_secret PRIMARY KEY (credential_id)
);

COMMENT ON TABLE flx_auth_credential_secret IS '认证凭证密钥表';
COMMENT ON COLUMN flx_auth_credential_secret.credential_id IS '共享主键, 关联 flx_auth_credential.id';
COMMENT ON COLUMN flx_auth_credential_secret.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_auth_credential_secret.encrypt_alg IS '加密算法';
COMMENT ON COLUMN flx_auth_credential_secret.kek_key_id IS '主密钥标识';
COMMENT ON COLUMN flx_auth_credential_secret.secret_ciphertext IS '敏感配置密文(JSON)';
COMMENT ON COLUMN flx_auth_credential_secret.secret_version IS '密钥版本';
COMMENT ON COLUMN flx_auth_credential_secret.create_time IS '创建时间';
COMMENT ON COLUMN flx_auth_credential_secret.update_time IS '更新时间';

-- 操作审计表
CREATE TABLE flx_operation_audit (
    id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    operator_id VARCHAR(64) DEFAULT NULL,
    operator_name VARCHAR(128) DEFAULT NULL,
    operation_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT DEFAULT NULL,
    target_code VARCHAR(128) DEFAULT NULL,
    request_id VARCHAR(64) DEFAULT NULL,
    request_ip VARCHAR(64) DEFAULT NULL,
    operation_content TEXT,
    result_status SMALLINT NOT NULL DEFAULT 1,
    error_message VARCHAR(1000) DEFAULT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_flx_operation_audit PRIMARY KEY (id)
);

COMMENT ON TABLE flx_operation_audit IS '操作审计表';
COMMENT ON COLUMN flx_operation_audit.id IS '主键';
COMMENT ON COLUMN flx_operation_audit.tenant_id IS '租户ID';
COMMENT ON COLUMN flx_operation_audit.operator_id IS '操作人ID';
COMMENT ON COLUMN flx_operation_audit.operator_name IS '操作人名称';
COMMENT ON COLUMN flx_operation_audit.operation_type IS '操作类型: CREATE, UPDATE, DELETE, PUBLISH, ENABLE, DISABLE';
COMMENT ON COLUMN flx_operation_audit.target_type IS '目标类型: FLOW, VERSION, ENDPOINT, JOB, RESOURCE';
COMMENT ON COLUMN flx_operation_audit.target_id IS '目标ID';
COMMENT ON COLUMN flx_operation_audit.target_code IS '目标业务编码';
COMMENT ON COLUMN flx_operation_audit.request_id IS '请求ID';
COMMENT ON COLUMN flx_operation_audit.request_ip IS '请求IP';
COMMENT ON COLUMN flx_operation_audit.operation_content IS '操作内容(JSON)';
COMMENT ON COLUMN flx_operation_audit.result_status IS '结果: 0-失败, 1-成功';
COMMENT ON COLUMN flx_operation_audit.error_message IS '失败原因摘要';
COMMENT ON COLUMN flx_operation_audit.create_time IS '创建时间';

COMMENT ON COLUMN flx_operation_audit.target_type IS 'Target type: FLOW, VERSION, ENDPOINT, JOB, RESOURCE, AUTH_CREDENTIAL';

CREATE INDEX idx_flx_operation_audit_tenant_target_time ON flx_operation_audit(tenant_id, target_type, target_id, create_time);
CREATE INDEX idx_flx_operation_audit_tenant_operator_time ON flx_operation_audit(tenant_id, operator_id, create_time);
