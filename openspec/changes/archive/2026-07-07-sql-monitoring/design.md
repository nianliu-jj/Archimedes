## Context

Archimedes 1.1 已落地配置中心（Slice 16）；本变更为 `docs/需求.md` 的最后一块缺口。既有约束延续：core 以 SB 2.7.18 编译面 + `--release 8` + 零 servlet import；可选生态一律 optional 依赖 + `@ConditionalOnClass`（先例：dubbo/grpc/HikariCP 同属此类）；控制器纯注解式双栈复用；UI 单文件 ES5。JDBC 标准接口（java.sql/javax.sql）在 JDK 内，SB2/SB3 无 javax/jakarta 分裂问题，代理逻辑可完全下沉 core。

## Goals / Non-Goals

**Goals:**

- 零侵入 SQL 监控：宿主任意 `DataSource` Bean 自动包装，标准 JDBC 代理捕获执行明细（SQL/参数/耗时/类型/行数/异常）。
- 与链路打通：执行记录携带当前 MDC traceId，可与链路日志相互印证。
- 聚合统计与慢 SQL：按数据源+归一化 SQL 聚合（次数/总耗时/平均/最大/失败），慢 SQL 阈值可配。
- 连接池可视化：HikariCP 池指标内置（optional 依赖），`PoolMetricsContributor` SPI 留其它池扩展位。
- 多数据源按 Bean 名区分；端点 + UI DB Tab 闭环。

**Non-Goals:**

- 不做 R2DBC/响应式数据访问监控（README 声明边界）。
- 不做 SQL 解析级参数化归一（Statement 字面量 SQL 按空白归一化聚合；PreparedStatement 天然带 `?` 占位）。
- 不做监控数据持久化（内存环形缓冲，重启清零；与日志采集同哲学）。
- 不代理 DatabaseMetaData / Savepoint 等非执行路径（原样透传）。

## Decisions

1. **包与组件**（core 新增 `jdbc` 包）
   - `SqlMonitorProperties`（`archimedes.sql.*`）：`enabled`(true) / `slow-sql-millis`(1000) / `max-history-size`(500，最近与慢 SQL 环形缓冲各自上限) / `capture-parameters`(true) / `exclude-beans`(空) / `max-sql-stats`(1000，去重 SQL 聚合条目上限)。
   - `SqlExecutionRecord`：dataSource/sql/params/startTime/durationMillis/type/rows/success/error/traceId/slow。查询行数在 ResultSet 关闭时回填（记录对象 volatile 可变字段，注册表持引用可见）。
   - `SqlStatRegistry`：`ConcurrentHashMap<数据源+SQL, SqlStat>` 原子累加（LongAdder/AtomicLong）；达到 `max-sql-stats` 上限后新 SQL 不再新建聚合槽（明细仍记录，WARN 一次）；`recent` 与 `slow` 两个有界双端队列（synchronized ArrayDeque，超限逐出最老）。
   - 代理三层：`MonitoringDataSource`（实现 DataSource + Wrapper 全透传，暴露 targetDataSource 与 beanName）→ Connection JDK 动态代理（拦截 create/prepare 三族方法）→ Statement 族 JDK 动态代理（拦截 execute*/addBatch/setXxx 参数捕获）→ 查询另加 ResultSet 代理（`next()==true` 计数，close 回填 fetchedRows）。选 JDK 动态代理而非字节码增强：零依赖、JDBC 全接口化天然适配。
   - `DataSourceMonitorBeanPostProcessor`：`postProcessAfterInitialization` 包装非排除、未包装的 DataSource Bean 并登记 `DataSourceMonitorRegistry`；以 **static @Bean** 注册避免装配类过早实例化告警（对齐 MdcExecutorBeanPostProcessor 先例）。
   - traceId 读取：构造时注入 MDC key（自动装配从 `TraceProperties.getMdcKey()` 取，缺省 "traceId"），jdbc 包不依赖 trace 包类型。
2. **池指标 SPI**：`PoolMetricsContributor`（`supports(DataSource)` + `metrics(DataSource)`）；`HikariPoolMetricsContributor` 直引 HikariCP 类型（optional 依赖），Bean 级 `@ConditionalOnClass(HikariDataSource.class)` 守护——Hikari 不在 classpath 时该类不加载（对齐 Dubbo/gRPC 扫描器模式）。指标：active/idle/awaiting/total + maximumPoolSize/minimumIdle。读取前先经 `unwrap` 触达原生 HikariDataSource。
3. **端点**：`ArchimedesDbController`（web 包，纯注解式）`GET {base}/db` 返回 `{slowSqlMillis, dataSources:[{name, targetType, pool|null}], sqlStats:[...], recentSqls:[...], slowSqls:[...]}`；统计与明细里的 dataSource 字段即 Bean 名（多数据源前端过滤）。
4. **包装兼容性**：`unwrap()/isWrapperFor()` 完整委托目标（Boot 的 `DataSourceUnwrapper`、actuator、Flyway 等经 Wrapper 链可达原生实现）；已知残余风险 = 按 `HikariDataSource` 具体类型注入的宿主代码拿到的是 DataSource 代理 → `archimedes.sql.exclude-beans` 逃生口 + README 声明。
5. **UI**：TABS 增加 `db`；池状态卡片（每数据源一张）+ SQL Stats 表 + Recent / Slow 列表；文本过滤复用配置中心模式（Tab 内独立搜索框）；手动 Refresh + Auto 复选框（5s 轮询，仅 Tab 激活时）。
6. **测试策略**：core 用 H2（test 依赖）直测代理闭环（DDL/DML/查询计数/批处理/异常/参数捕获/归一化）；starter e2e 用 `spring-boot-starter-jdbc` + H2（Boot 默认池即 Hikari）真 HTTP 验证 `/db` 全字段与慢 SQL（`slow-sql-millis=0` 强制全慢）；SB2 镜像。

## Risks / Trade-offs

- [按 HikariDataSource 具体类型注入的宿主 Bean 注入失败] → Wrapper 透传 + exclude-beans 逃生 + README 声明；Boot 自身组件均走 DataSource 接口/unwrap，主流场景安全。
- [参数捕获可能含敏感数据] → `capture-parameters=false` 可关；参数仅入内存环形缓冲不落盘。
- [高并发下 recent/slow 队列锁竞争] → 单条入队临界区极小（addLast+超限 removeFirst）；统计走无锁累加，热点路径不受影响。
- [ResultSet 行数在 close 前查询端点可能读到 -1] → 前端展示 "-" 语义为"未完成/未知"，文档化。
- [Statement 字面量 SQL 聚合粒度粗] → 记录为独立条目直至 max-sql-stats 上限，超限只记明细；Druid 级 SQL 解析明确列为非目标。

## Migration Plan

纯新增能力，默认开启；`archimedes.sql.enabled=false` 一键关闭（BPP 不注册、DataSource 不包装、端点不装配），回退零残留。
