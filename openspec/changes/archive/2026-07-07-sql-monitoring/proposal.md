## Why

`docs/需求.md` §4 要求的「HikariCP 连接池与 SQL 监控（参照 Druid）」尚未实现：宿主应用无法观测连接池运行状态，也无法看到 SQL 执行明细（语句/参数/耗时/行数/异常）与聚合统计。作为 1.1 版本最后一块缺口能力，本变更补齐数据库监控。

## What Changes

- 数据源代理：`BeanPostProcessor` 自动将容器内 `DataSource` Bean 包装为 `MonitoringDataSource`（JDBC 标准接口动态代理，类似 p6spy/Druid Filter），拦截 Statement/PreparedStatement/CallableStatement 执行。
- SQL 执行记录：语句（空白归一化）、参数（可关）、开始时间、耗时、类型（QUERY/UPDATE/BATCH/EXECUTE）、影响/返回行数（查询经 ResultSet 代理计数）、成功/异常信息、**traceId 关联**（从 MDC 读取，与链路日志打通）。
- 统计聚合：按「数据源 + 归一化 SQL」维度聚合执行次数/总耗时/平均/最大/失败次数；最近执行环形缓冲 + 慢 SQL 列表（阈值 `archimedes.sql.slow-sql-millis`，默认 1000ms）；去重 SQL 条目数设上限防膨胀。
- HikariCP 连接池指标：`PoolMetricsContributor` SPI + Hikari 内置实现（`@ConditionalOnClass`，HikariCP 为 optional 编译依赖）——活跃/空闲/等待线程/总连接 + 最大池/最小空闲配置。
- 多数据源：按 Bean 名区分，端点与 UI 可按数据源查看。
- 查询端点 `GET {base-path}/db`：数据源列表（含池指标）+ SQL 统计 + 最近执行 + 慢 SQL。
- 内置 UI 新增「DB」Tab：连接池状态卡片、SQL 统计表（可排序意义上的字段完整输出）、最近/慢 SQL 列表、搜索过滤、手动/自动刷新。
- 新增配置 `archimedes.sql.*`：enabled / slow-sql-millis / max-history-size / capture-parameters / exclude-beans / max-sql-stats。
- SB2/SB3 双 starter 同步装配；example-all 增加 H2 + JdbcTemplate 演示端点。

## Capabilities

### New Capabilities

- `sql-monitoring`: 数据源代理 SQL 执行监控（明细/统计/慢 SQL/traceId 关联/多数据源）+ 连接池指标（HikariCP 内置支持，SPI 可扩展）+ 查询端点与 UI DB Tab。

### Modified Capabilities

<!-- 无：现有 spec 需求不变，UI 新 Tab 归属 sql-monitoring 能力自身 -->

## Impact

- `archimedes-core`：新增 `io.github.nianliu.archimedes.jdbc` 包（属性、执行记录模型、统计注册表、数据源/连接/语句代理、BeanPostProcessor、池指标 SPI + Hikari 实现）与 `web/ArchimedesDbController`；`archimedes-ui/index.html` 新增 Tab；pom 增加 `com.zaxxer:HikariCP`（optional）与 `com.h2database:h2`（test）。
- 双 starter：新增 `ArchimedesSqlMonitorAutoConfiguration` 注册（imports / spring.factories）；测试增加 `spring-boot-starter-jdbc` + `h2`（test scope）。
- `example-all`：增加 JDBC 依赖与订单查询演示端点（真实 SQL 流量供 UI 演示）。
- 风险面：包装后的 DataSource 为代理对象——`unwrap()/isWrapperFor()` 全量透传保证 `DataSourceUnwrapper` 类工具可达原生 Hikari；按具体类型注入 `HikariDataSource` 的宿主可用 `exclude-beans` 逃生（README 声明）。
- 边界：仅 JDBC（R2DBC 不在本期）；Statement 字面量 SQL 不做参数化归一（仅空白归一化）。
