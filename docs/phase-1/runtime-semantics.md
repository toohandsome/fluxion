# Fluxion 一期运行语义规范

## 1. 文档目的

本文档用于固化一期执行引擎的运行语义，覆盖起始节点判定、DAG 调度、分支汇聚、并发、超时、重试、失败传播和同步/异步触发行为。

一期执行语义以“最小可用闭环”为目标，不引入子流程、循环节点、补偿编排和人工任务。

## 2. 适用范围

一期仅适用于以下节点类型：

1. `log`
2. `variable`
3. `condition`
4. `http`
5. `dbQuery`
6. `dbUpdate`

说明：

1. 一期主流程必须是 DAG。
2. 一期不引入显式 `start` / `end` 节点类型。
3. 一期不支持子流程、循环和节点级自定义并行度。

## 3. 编译期结构规则

### 3.1 起始节点与结束节点

1. 入度为 `0` 的节点定义为起始节点。
2. 出度为 `0` 的节点定义为结束节点。
3. 一个流程至少必须存在一个起始节点和一个结束节点。
4. 一个流程允许存在多个起始节点和多个结束节点。

### 3.2 连通性约束

1. 所有节点都必须从至少一个起始节点可达。
2. 所有节点都必须可以到达至少一个结束节点。
3. 所有节点在忽略边方向后必须属于同一个连通分量。
4. 所有节点都必须位于至少一条“起始节点 -> 结束节点”的有效路径上。
5. 不允许存在独立子图；即使独立子图自身也满足起始节点和结束节点规则，仍视为非法。
6. 不允许存在孤岛节点。
7. 不允许存在回边和自循环。

### 3.3 条件节点约束

1. 条件节点固定只有 `true` 和 `false` 两个输出端口。
2. 条件节点的每条输出边必须显式绑定到 `true` 或 `false`。
3. 同一个输出端口允许连接多个下游节点。

## 4. 运行时实例模型

### 4.1 流程实例状态

| 状态 | 说明 |
| --- | --- |
| `CREATED` | 实例已创建，尚未进入调度 |
| `RUNNING` | 实例执行中 |
| `SUCCESS` | 实例成功结束 |
| `FAILED` | 实例失败结束 |
| `CANCELLED` | 实例被取消 |

### 4.2 节点执行状态

| 状态 | 说明 |
| --- | --- |
| `CREATED` | 节点执行记录已创建 |
| `RUNNING` | 节点正在执行 |
| `SUCCESS` | 节点执行成功 |
| `FAILED` | 节点执行失败且重试已耗尽 |
| `CANCELLED` | 节点被取消 |
| `SKIPPED` | 节点因分支未命中而跳过 |

约定：

1. `SKIPPED` 只用于“分支未命中或全部有效上游路径失效后，节点已被明确判定为不会执行”的场景。
2. `SKIPPED` 不用于表示“流程因 fail-fast 提前结束后，尚未来得及进入调度判定的节点”。

### 4.3 运行上下文

一期运行时表达式固定基于以下稳定命名空间求值：

1. `request`
   HTTP 触发时包含 `path`、`query`、`headers`、`body`
2. `schedule`
   调度触发时包含 `jobId`、`triggerTime`、`scheduledFireTime`、`params`
3. `vars`
   流程全局变量上下文
4. `instance`
   实例元数据，如 `instanceId`、`flowCode`、`triggerType`、`traceId`、`businessKey`
5. `nodes`
   已形成稳定输出的节点结果快照，按 `nodeId` 建立键空间；每个节点至少包含 `status` 和 `output`

约定：

1. 节点级表达式只允许依赖 `request`、`schedule`、`vars`、`instance`、`nodes` 这五类稳定命名空间。
2. 不允许使用裸变量名访问链路信息；例如应使用 `${instance.traceId}`，而不是 `${traceId}`。
3. 节点原始输入输出主要用于审计和调试，不作为跨节点稳定契约。
4. 当前节点执行器返回的原始结果只以阶段性局部命名空间 `raw` 暴露给本节点 `outputMapping`，不会进入全局稳定上下文。
5. 流程级最终输出在实例成功结束后，以只读视图 `flow.output` 暴露给 HTTP 响应映射和结果查询阶段；节点级表达式不直接访问 `flow`。

### 4.4 节点输入输出映射语义

一期节点执行固定遵循以下顺序：

1. 使用稳定命名空间计算当前节点 `inputMapping`。
2. 将 `inputMapping` 的求值结果组装为本节点 `resolvedInput`。
3. `NodeRunner` 消费 `resolvedInput` 并返回本次执行的 `raw` 原始结果。
4. 使用稳定命名空间加当前节点局部 `raw` 计算 `outputMapping`。
5. `outputMapping` 产出当前节点的稳定输出，并写入 `nodes.<nodeId>.output`。

补充约定：

1. `inputMapping` 只负责组装当前节点执行输入，不直接修改 `vars`。
2. `outputMapping` 只负责将 `raw` 归一为当前节点稳定输出，不直接修改 `vars`。
3. 如需把某节点结果写入共享变量，必须通过显式 `variable` 节点完成。
4. 同一份 `inputMapping` 或 `outputMapping` 内的表达式基于同一快照求值，批量提交，不允许依赖同批次其他字段的写入顺序。
5. 节点成功结束后，`nodes.<nodeId>.status = SUCCESS` 且 `nodes.<nodeId>.output` 可供后续节点访问。
6. `SKIPPED` 节点允许只有 `status` 而没有 `output`；后续表达式对缺失字段按 `null` 处理。
7. `outputMapping` 求值成功后，应立即写入实例内存态上下文 `nodes.<nodeId>.output`，随后在节点终态落库事务中同步持久化节点输出快照。

### 4.4.1 节点持久化与事务边界

1. 一期不提供分布式事务；节点对外部系统的调用与引擎自身数据库写入不处于同一事务。
2. 节点真实执行阶段结束后，节点终态、节点输出快照和错误摘要应在同一个本地事务内写入：
   - `flx_node_execution`
   - `flx_node_execution_data`（若本次需要记录大字段）
3. `flx_node_execution_attempt` 的状态推进也应保持本地事务一致性；单次 attempt 的开始、成功、失败分别按各自状态推进事务写入。
4. 若节点执行过程中已拿到外部结果，但在写库前进程崩溃，则该次 attempt 视为未完成恢复场景，由实例恢复逻辑按现有 `at-least-once` 语义处理。
5. 一期不要求节点中间态输入/输出采用批量延迟持久化；推荐在每个节点形成终态时落库，以降低实例级批量提交导致的一致性风险。

### 4.5 流程最终输出

一期发布后的运行时模型必须包含显式 `flowOutputMapping`，用于计算流程最终输出。

规则如下：

1. `flowOutputMapping` 在所有有效节点成功结束后执行。
2. `flowOutputMapping` 只允许依赖稳定命名空间 `request`、`schedule`、`vars`、`instance`、`nodes`。
3. `flowOutputMapping` 的求值结果写入流程实例最终输出快照。
4. `flow.output` 是对该最终输出快照的只读表达式视图。
5. HTTP 默认成功响应和异步结果查询中的 `result` 均以 `flow.output` 为准，不再约定使用 `vars.result`。
6. 草稿阶段允许暂缺 `flowOutputMapping`；发布校验阶段若缺失，必须报错。
7. `flowOutputMapping` 不做按节点增量求值；仅在所有有效节点成功结束后统一求值一次。
8. 若 `flowOutputMapping` 求值发生 SpEL 异常或结果构造失败，则流程实例记为 `FAILED`，错误码建议使用 `FLOW_OUTPUT_EVAL_FAILED`。
9. `flowOutputMapping` 求值失败时，不引入额外的特殊实例状态。

## 5. DAG 调度语义

### 5.1 实例启动

1. 创建实例后，所有起始节点立即进入就绪队列。
2. 多个起始节点可以并发执行。
3. 实例状态在首批节点开始执行前切换为 `RUNNING`。

### 5.2 节点就绪条件

普通节点的就绪条件如下：

1. 若节点入度为 `0`，则实例启动时立即就绪。
2. 若节点存在多个上游，则采用隐式 `AND-join` 语义。
3. 一个节点只有在所有“有效上游”都成功结束后才可执行。
4. “有效上游”是指本次运行路径上实际命中的上游分支。
5. 因条件分支未命中而失效的上游，不计入等待集合。

### 5.3 条件分支语义

1. 条件节点执行表达式，结果归一为 `true` 或 `false`。
2. 当表达式为 `null` 时，按节点配置 `nullAsFalse` 处理。
3. 命中的分支继续向下游传播执行令牌。
4. 未命中的分支视为失效路径。
5. 若某个下游节点的全部上游路径都失效，则该节点记为 `SKIPPED`。

### 5.3.1 `SKIPPED` 持久化语义

1. 当调度器已经对某节点得出“本次实例不会执行”的明确结论时，必须写入一条 `flx_node_execution` 记录。
2. `flx_node_execution.status = SKIPPED`。
3. `start_time` 和 `end_time` 取跳过判定时刻，`duration_ms = 0`。
4. `attempt_count = 0`，`retry_count = 0`。
5. `skip_reason` 记录跳过原因；一期建议至少支持 `BRANCH_NOT_MATCHED` 和 `ALL_UPSTREAM_PATHS_INVALIDATED`。
6. `SKIPPED` 节点默认不要求写入 `flx_node_execution_data`；如需增强可观测性，可额外写入跳过说明，但这不作为一期强制要求。

### 5.4 汇聚语义

一期不引入显式 Join 节点，汇聚采用隐式规则：

1. 多入边节点默认表示“等待所有有效上游成功”。
2. 只要仍有有效上游未完成，节点不得提前执行。
3. 任何一个有效上游失败后，流程立即按失败处理，不再等待后续汇聚。

### 5.5 节点执行次数

1. 一期 DAG 内每个节点在单次实例中最多执行一次。
2. 节点重试只增加 attempt，不产生新的 node key。
3. 一期不支持循环回到同一节点再次执行。

## 6. 并发、超时与重试

### 6.1 并发语义

1. 处于就绪态的多个节点允许并发执行。
2. 并发只针对 I/O 型节点场景优化。
3. 实际并发度由引擎执行器全局上限控制。
4. `flx_schedule_job.max_concurrency` 只控制同一调度任务可同时运行的实例数，不控制单个实例内部节点并发。
5. 一期不开放节点级并行度配置。
6. `log`、`variable`、`condition` 等轻量 CPU 节点默认内联执行，不额外切换执行线程。
7. `http`、`dbQuery`、`dbUpdate` 等 I/O 节点允许并发，由引擎执行器统一调度。

### 6.1.1 资源级并发保护

1. 对 `http`、`dbQuery`、`dbUpdate` 这类依赖外部资源的节点，一期在执行前增加基于 `resourceRef` 的并发许可校验。
2. 资源级并发许可用于保护外部服务、数据库连接和下游容量，不作为节点参数暴露给流程设计器。
3. 一期并发许可的配额来源于引擎或资源侧配置，不引入节点级单独配置。
4. 一期资源级并发许可采用基于 `resourceRef` 的 `Semaphore` 语义实现，目标是控制同时进入下游资源调用的节点数。
5. 一期许可获取采用非阻塞 `tryAcquire` 语义，不引入排队等待、许可预约或令牌桶速率控制。
6. 一期不引入独立排队状态和 `WAITING_FOR_PERMIT` 一类新状态；节点拿不到许可时，按一次失败 attempt 处理，并继续复用既有超时/重试语义。
7. 节点因资源许可不足失败时，建议错误码使用 `RESOURCE_PERMIT_EXHAUSTED`。
8. 资源级并发保护属于引擎内部治理能力，不改变 `flx_schedule_job.max_concurrency` 的实例级含义。

### 6.2 超时语义

1. `timeoutMs` 按单次 attempt 生效。
2. 节点单次 attempt 超时后，视为本次 attempt 失败。
3. 若仍有剩余重试次数，则等待 `retryIntervalMs` 后进入下一次 attempt。
4. 若无剩余重试次数，则节点状态置为 `FAILED`。

### 6.3 重试语义

1. `retry = 0` 表示失败后不重试。
2. 总尝试次数固定为 `1 + retry`。
3. 一次真实 attempt 从节点开始申请执行资源起计算，包含资源许可获取、实际执行和失败回收过程；每次真实 attempt 都必须写入 `flx_node_execution_attempt`。
4. `SKIPPED` 节点不生成 `flx_node_execution_attempt` 记录。
5. 节点最终成功时，`flx_node_execution.status = SUCCESS`。
6. 节点最终失败时，`flx_node_execution.status = FAILED`，并记录错误摘要和最后一次异常。
7. 若节点因 `resourceRef` 并发许可不足未能进入下游资源调用，该次也视为一次失败 attempt，并按现有重试规则处理。
8. 资源许可不足不会导致无限重试；其重试上限与普通执行失败一致，固定受 `retry` 控制。

### 6.4 副作用节点策略

1. 对 `http` 和 `dbUpdate` 这类访问外部系统或数据源的节点，发布前必须完成副作用策略校验。
2. 当 `sideEffectPolicy.type = READ_ONLY` 时，节点按普通只读节点执行。
3. 当 `sideEffectPolicy.type = COMPENSABLE` 时，一期只记录补偿声明，不自动执行补偿动作。

## 7. 失败传播与结束条件

### 7.1 失败传播

1. 一期默认策略为“节点失败即流程失败”。
2. 任一有效节点在重试耗尽后失败，流程实例立即转为 `FAILED`。
3. 流程失败后，不再调度新的下游节点。
4. 已经在运行中的节点可等待其当前 attempt 结束后回收，但其结果不再改变流程最终状态。
5. 流程进入 fail-fast 后，对尚未形成调度结论的节点不补写 `SKIPPED`，也不补写 `flx_node_execution`。

### 7.1.1 fail-fast 下的监控展示语义

1. `SKIPPED` 只用于调度器已明确得出“本次不会执行”结论的节点，不用于表示 fail-fast 后尚未进入调度判定的节点。
2. 若流程实例已进入 `FAILED`，某节点存在于 `model_json` 中，但运行态未生成任何 `flx_node_execution` 记录，则说明该节点在 fail-fast 前尚未形成调度结论。
3. Admin 监控页或执行详情页可将上述“模型中存在但无执行记录”的节点派生展示为 `NOT_SCHEDULED` 或 `ABORTED_BY_FAIL_FAST`。
4. `NOT_SCHEDULED` / `ABORTED_BY_FAIL_FAST` 属于监控展示派生态，不属于一期正式持久化状态，也不回写数据库。
5. 一期数据库与运行态正式状态仍只使用 `CREATED`、`RUNNING`、`SUCCESS`、`FAILED`、`CANCELLED`、`SKIPPED` 六类状态。

### 7.2 成功结束

流程满足以下条件时判定为成功：

1. 所有有效节点都已进入终态。
2. 所有有效节点终态均为 `SUCCESS`。
3. 失效路径上的节点允许为 `SKIPPED`。

### 7.3 取消结束

1. 一期保留 `CANCELLED` 状态，但不提供正式的外部取消接口。
2. 该状态仅作为后续扩展和内部保护位预留。

## 8. 同步与异步触发行为

### 8.1 同步触发

1. 同步触发会等待流程进入终态，或等待到端点级 `timeoutMs`。
2. 若实例在超时前完成，则直接返回最终结果。
3. 若实例在超时前未完成，则返回 `SYNC_TIMEOUT`。
4. 返回 `SYNC_TIMEOUT` 时，实例继续在后台运行。
5. 同步超时响应中必须返回 `instanceId` 和 `queryUrl`。

### 8.2 异步触发

1. 异步触发只负责创建实例并提交执行。
2. 实例创建成功后立即返回 `ACCEPTED`。
3. 异步首次响应不等待流程完成。
4. 调用方通过 `GET /runtime/instances/{instanceId}/result` 查询最终结果。

### 8.3 结果查询

1. 当实例仍在运行时，返回 `INSTANCE_RUNNING`。
2. 当实例成功结束时，返回 `OK`。
3. 当实例失败结束时，返回 `FLOW_FAILED` 或更细粒度业务错误码。
4. 所有结果查询接口都固定返回 HTTP `200`。

## 9. 设计取舍

一期采用以下固定取舍：

1. 起始节点采用“入度为 0”规则，不引入 `start` 节点类型。
2. 汇聚采用隐式 `AND-join`，不引入显式 Join 节点。
3. 失败采用 fail-fast，不做继续执行和补偿编排。
4. 同步超时后实例继续后台运行，避免调用方超时直接中断引擎执行。
