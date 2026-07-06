# Tasks: log-capture-query

## 1. core log 包

- [x] 1.1 `log/LogEntry`（epochMillis+格式化时间/level/thread/logger/message/spanId/traceId）与 `log/LogQueryResult`
- [x] 1.2 `log/LogStore` SPI + `log/InMemoryLogStore`（双上限、最老 trace 整体淘汰、快照排序分页）
- [x] 1.3 `log/ArchimedesLogAppender`（AppenderBase，MDC 无 traceId 跳过）+ `log/LogCaptureInitializer`（挂载/卸载、非 logback 跳过）
- [x] 1.4 `log/LogCaptureProperties`（archimedes.log.capture.*：enabled/max-entries/max-entries-per-trace）
- [x] 1.5 `web/ArchimedesLogController`（logs/trace/{traceId} 分页 + trace/current）
- [x] 1.6 core pom 增加 logback-classic optional；单测：存储（排序/分页/双上限淘汰）、Appender（捕获/无 traceId 跳过）挂载往返

## 2. starter 装配

- [x] 2.1 双 starter `ArchimedesLogAutoConfiguration`（ConditionalOnClass LoggerContext + capture 开关 + SERVLET）+ 注册行
- [x] 2.2 装配测试：默认有 LogStore/Controller；capture.enabled=false 零 Bean；自定义 LogStore Bean 让位

## 3. UI 与端到端

- [x] 3.1 index.html 增加 Trace Logs 分区（traceId 输入查询、时间线表格、线程徽标区分）
- [x] 3.2 双端 e2e：请求（带 @Async 双线程日志）→ 查询端点断言两线程日志归一、分页与排序、trace/current
- [x] 3.3 全量构建全绿 + example 真机验证（调 /api/trace/async 后按 traceId 查回双线程日志）

## 4. 收尾

- [x] 4.1 README 日志查询章节（端点、容量语义、ES 预留位说明）
- [x] 4.2 功能清单勾选 Slice 6 + spec 同步 + 归档
