# Proposal: log-capture-query

## Why

需求核心场景：调用接口后，能按 traceId 查出该链路（含多线程）产生的全部日志（`docs/项目需求.md` §五）。Slice 4/5 已保证 traceId 进 MDC 且跨线程传递；本 slice 落地采集与查询闭环，让"查一条链路的日志"从能力变成端点与页面。

## What Changes

- core 新增 `log` 包：
  - `LogEntry` 模型（epochMillis + 格式化时间、level、thread、logger、message、spanId、traceId）
  - `LogStore` SPI：`append(LogEntry)` / `queryByTraceId(traceId, page, size)`——**Elasticsearch 等持久化实现的预留接入位**（`@ConditionalOnMissingBean` 让位）
  - `InMemoryLogStore` 默认实现：按 traceId 索引的有界内存存储（全局条数上限 + 单 trace 条数上限，超限按最老 trace 整体淘汰）
  - `ArchimedesLogAppender extends AppenderBase<ILoggingEvent>`：**编程式挂到 root logger**，结构化捕获（读 `ILoggingEvent` 与 MDC），与用户日志格式完全解耦；无 traceId 的日志不采集
  - `LogCaptureInitializer`：logback 在场时启动挂载/销毁卸载；非 logback 环境打日志说明并跳过
  - `LogCaptureProperties`（`archimedes.log.capture.*`）：enabled / max-entries / max-entries-per-trace
  - `web/ArchimedesLogController`：`GET {base-path}/logs/trace/{traceId}`（分页、时间排序）+ `GET {base-path}/trace/current`
- 各 starter 新增 `ArchimedesLogAutoConfiguration`（`@ConditionalOnClass(LoggerContext)` + capture 开关 + SERVLET web）并注册
- 内置 UI 新增 **Trace Logs** 分区：输入 traceId 查询、时间线表格、多线程条目高亮区分
- example×2 已有的多线程演示端点即为演示数据源

## Capabilities

### New Capabilities

- `log-capture-query`: 基于 Appender 的结构化日志采集（格式解耦）、LogStore SPI 与内存默认实现、按 traceId 的分页查询端点与 UI。

### Modified Capabilities

（无）

## Impact

- **core**：新增 `log` 包 + `logback-classic` optional 依赖（编译面 API 在 1.2.x/1.5.x 一致：AppenderBase/ILoggingEvent/LoggerContext）
- **starter×2**：各一个新自动装配类 + 注册行
- **UI**：index.html 第三分区
- **采集边界**：仅捕获 MDC 含 traceId 的日志事件；默认内存实现重启即失、有界淘汰（文档明示，生产持久化走 LogStore SPI）
