## 1. core：jdbc 包（属性/模型/统计）

- [x] 1.1 `SqlMonitorProperties`（archimedes.sql.*：enabled / slow-sql-millis / max-history-size / capture-parameters / exclude-beans / max-sql-stats）+ 绑定单测
- [x] 1.2 `SqlExecutionRecord` 模型（含 traceId、slow、rows 回填）与 SQL 空白归一化工具
- [x] 1.3 `SqlStatRegistry`：聚合累加（次数/总耗时/平均/最大/失败）+ recent/slow 有界队列 + max-sql-stats 上限；单测覆盖聚合/慢标记/淘汰/上限

## 2. core：JDBC 代理与 BPP

- [x] 2.1 `MonitoringDataSource`（Wrapper 全透传）+ Connection/Statement/ResultSet 三层 JDK 动态代理（execute 族计时、setXxx 参数捕获、批处理、查询行数回填、异常记录后原样抛出）
- [x] 2.2 H2 直连单测：DDL/DML/查询计数/批处理/异常/参数捕获/归一化/traceId 关联全覆盖
- [x] 2.3 `DataSourceMonitorRegistry` + `DataSourceMonitorBeanPostProcessor`（static @Bean、exclude-beans、防双包装）+ 单测
- [x] 2.4 `PoolMetricsContributor` SPI + `HikariPoolMetricsContributor`（optional 依赖 + unwrap 触达原生）+ 单测（真实 HikariDataSource）

## 3. core：端点与 UI

- [x] 3.1 `ArchimedesDbController`：GET {base}/db 全结构（dataSources/sqlStats/recentSqls/slowSqls/slowSqlMillis）+ 单测
- [x] 3.2 `index.html` 新增 DB Tab：池状态卡片 + SQL 统计表 + Recent/Slow 列表 + 搜索 + 手动/自动刷新

## 4. starter：双端装配与 e2e

- [x] 4.1 SB3 `ArchimedesSqlMonitorAutoConfiguration`（api/sql 双开关 + TraceProperties mdc-key 接线）+ imports 注册 + 装配单测
- [x] 4.2 SB2 同名装配 + spring.factories 注册 + 装配单测
- [x] 4.3 SB3 e2e（starter-jdbc + H2 真 HTTP）：Hikari 池指标 / 统计 / 慢 SQL / traceId 关联 / enabled=false 404
- [x] 4.4 SB2 e2e：镜像 SB3 用例（javax 栈）

## 5. 验证与收尾

- [x] 5.1 全模块 `mvn test` 全绿；example-all 增加 H2 + JdbcTemplate 订单演示端点（真实 SQL 流量）
- [x] 5.2 更新 README（数据库监控章节 + archimedes.sql.* 配置表 + R2DBC/具体类型注入边界声明）与 docs/功能清单与任务列表.md
