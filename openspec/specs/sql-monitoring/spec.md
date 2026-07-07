# sql-monitoring Specification

## Purpose
TBD - created by archiving change sql-monitoring. Update Purpose after archive.
## Requirements
### Requirement: DataSource 自动代理与多数据源
系统 SHALL 通过 `BeanPostProcessor` 将容器内全部 `DataSource` Bean 自动包装为监控代理（已包装与 `archimedes.sql.exclude-beans` 命中的除外），包装 SHALL 完整透传 `Wrapper.unwrap()/isWrapperFor()` 使调用方可触达原生实现；多个数据源 SHALL 按 Bean 名称区分并全部纳入监控。`archimedes.sql.enabled=false` 时 SHALL 不做任何包装且监控组件不装配。

#### Scenario: 数据源被自动包装
- **WHEN** 宿主定义任意 DataSource Bean 且未显式排除
- **THEN** 注入方拿到的是监控代理，`unwrap(原生类型)` 仍可达目标实现，SQL 执行开始被记录

#### Scenario: 排除逃生口
- **WHEN** 宿主配置 `archimedes.sql.exclude-beans=rawDs` 且存在名为 rawDs 的 DataSource Bean
- **THEN** 该 Bean 不被包装、不出现在监控数据源列表中，其余数据源不受影响

### Requirement: SQL 执行明细记录
监控代理 SHALL 拦截 Statement/PreparedStatement/CallableStatement 的执行方法，记录：归一化 SQL（空白折叠）、参数列表（`capture-parameters=false` 时不采集）、开始时间、耗时毫秒、执行类型（QUERY/UPDATE/BATCH/EXECUTE）、行数（更新为影响行数，查询经 ResultSet 代理计数、close 时回填）、成功/异常消息，以及执行线程 MDC 中的 traceId（无则为空）。执行异常 SHALL 记录后原样抛出，不改变业务语义。

#### Scenario: 更新语句记录影响行数
- **WHEN** 业务执行 `UPDATE` 影响 3 行
- **THEN** 明细记录 type=UPDATE、rows=3、success=true，且 SQL 已空白归一化

#### Scenario: 查询语句回填返回行数
- **WHEN** 业务执行查询并遍历完 ResultSet 后关闭
- **THEN** 该执行明细的 rows 等于实际取回行数

#### Scenario: 失败 SQL 记录异常且原样抛出
- **WHEN** 业务执行非法 SQL
- **THEN** 调用方收到原始 SQLException，明细记录 success=false 且含异常消息，失败计数 +1

#### Scenario: traceId 关联
- **WHEN** 带 traceId 的请求线程内执行 SQL
- **THEN** 该执行明细的 traceId 与请求 traceId 一致

### Requirement: 统计聚合与慢 SQL
系统 SHALL 按「数据源 + 归一化 SQL」聚合：执行次数、总耗时、平均耗时、最大耗时、失败次数；SHALL 维护最近执行与慢 SQL 两个有界列表（上限 `archimedes.sql.max-history-size`，默认 500，超限逐出最老）；耗时超过 `archimedes.sql.slow-sql-millis`（默认 1000）的执行 SHALL 标记为慢 SQL 并进入慢列表。去重 SQL 聚合条目数达到 `archimedes.sql.max-sql-stats` 上限后 SHALL 停止新建聚合槽（明细仍记录）并告警一次。

#### Scenario: 同一 SQL 多次执行聚合
- **WHEN** 同一条 SQL 执行 3 次（含 1 次失败）
- **THEN** 该 SQL 的聚合条目 executionCount=3、errorCount=1，平均/最大耗时正确

#### Scenario: 慢 SQL 识别
- **WHEN** 某执行耗时超过 slow-sql-millis 阈值
- **THEN** 该明细 slow=true 且出现在慢 SQL 列表

#### Scenario: 有界淘汰
- **WHEN** 最近执行条数超过 max-history-size
- **THEN** 最老的明细被逐出，列表长度不超过上限

### Requirement: 连接池指标（HikariCP 内置 + SPI）
系统 SHALL 提供 `PoolMetricsContributor` SPI（supports + metrics）；classpath 存在 HikariCP 时 SHALL 内置 Hikari 实现（optional 依赖 + `@ConditionalOnClass`），对可 unwrap 到 `HikariDataSource` 的数据源输出：活跃连接数、空闲连接数、等待线程数、总连接数、最大池大小、最小空闲数。非 Hikari 数据源无匹配贡献者时池指标 SHALL 为空但 SQL 监控不受影响。

#### Scenario: Hikari 池指标可见
- **WHEN** 宿主使用 HikariCP（Boot 默认）且发生过连接获取
- **THEN** `/db` 端点该数据源的 pool 字段包含 active/idle/awaiting/total/maximumPoolSize/minimumIdle

#### Scenario: 无 Hikari 时优雅降级
- **WHEN** classpath 无 HikariCP 或数据源非 Hikari
- **THEN** pool 字段为 null，SQL 明细与统计正常

### Requirement: 数据库监控端点
系统 SHALL 提供 `GET {base-path}/db` 端点返回：慢 SQL 阈值、数据源列表（Bean 名、目标类型、池指标）、SQL 聚合统计、最近执行明细、慢 SQL 明细；各明细与统计条目 SHALL 携带数据源 Bean 名以支持前端按数据源过滤。`archimedes.sql.enabled=false` 时端点 SHALL 返回 404。

#### Scenario: 端点返回完整结构
- **WHEN** 应用执行过若干 SQL 后请求 `GET {base-path}/db`
- **THEN** 响应包含 dataSources / sqlStats / recentSqls / slowSqls / slowSqlMillis 字段且数据一致

### Requirement: 数据库监控 UI Tab
内置控制台 SHALL 新增「DB」Tab：按数据源展示连接池状态卡片、SQL 统计表（SQL/次数/平均/最大/失败/行数）、最近执行与慢 SQL 列表（含 traceId、耗时、行数、异常）、文本搜索过滤、手动刷新与可开关的自动刷新（仅 Tab 激活时轮询）。

#### Scenario: 查看 SQL 统计与慢 SQL
- **WHEN** 用户打开 DB Tab
- **THEN** 页面展示池状态卡片与 SQL 统计表，慢 SQL 列表中的条目可见耗时与 traceId

### Requirement: 双栈与双版本装配
数据库监控 SHALL 在 SB2/SB3 双 starter 以独立自动装配类注册（SB3 AutoConfiguration.imports / SB2 spring.factories），受 `archimedes.api.enabled` 与 `archimedes.sql.enabled` 双开关控制；监控范围为 JDBC `DataSource`（R2DBC 不在本期范围，文档声明）。

#### Scenario: 双端行为一致
- **WHEN** 同一宿主应用分别运行于 SB2.7 与 SB3
- **THEN** `/db` 端点结构与监控行为一致

