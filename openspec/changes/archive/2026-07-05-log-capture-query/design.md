# Design: log-capture-query

## Context

需求文档的方案一是"文件存储 + 正则匹配 traceId"，但日志格式可自定义（Slice 7），格式一变解析即断。explore 阶段已定：**Appender 结构化捕获**——直接消费 `ILoggingEvent` 对象与其 MDC map，与输出格式零耦合。存储默认内存（用户已确认），SPI 为 ES 预留。

## Goals / Non-Goals

**Goals:**
- 引入即用：不要求用户改任何 logback 配置，Appender 编程式挂载 root logger
- 查询闭环：分页、按时间排序、多线程条目可区分（thread 字段）
- SPI 形状稳定：ES/文件等持久化后续实现零改动接入

**Non-Goals:**
- ES 客户端实现（预留位，后续 slice）
- 日志格式/文件配置（Slice 7）
- log4j2/JUL 支持（logback-only，非 logback 环境优雅跳过）

## Decisions

### D1：编程式挂载 Appender，而非要求用户配置文件引用

`LogCaptureInitializer`（InitializingBean/DisposableBean）检测 `LoggerFactory.getILoggerFactory() instanceof LoggerContext`，是则创建 `ArchimedesLogAppender` 挂到 ROOT logger，销毁时卸载。用户的 logback-spring.xml 是否存在、pattern 长什么样都无关——这正是"格式解耦"的落点。非 logback（log4j2）环境：INFO 日志说明采集不可用，其余功能不受影响。

### D2：无 traceId 不采集

Appender 读 `event.getMDCPropertyMap().get(mdcKey)`（key 跟随 `archimedes.trace.mdc-key` 配置），为空直接返回。理由：查询入口只有 traceId，无主日志采了也查不到，白占内存；且启动期/定时任务日志天然无 traceId，排除后内存全给业务链路。

### D3：内存存储的淘汰语义——"最老 trace 整体淘汰"

结构：`LinkedHashMap<traceId, Deque<LogEntry>>`（插入序）+ 全局计数。单 trace 超 `max-entries-per-trace`（默认 500）丢弃该 trace 最老条目；全局超 `max-entries`（默认 10000）按插入序淘汰**整个最老 trace**。选择整 trace 淘汰而非全局逐条：实现 O(1)、语义可解释（"最近的链路完整可查"），避免跨 map 逐条剔除的 O(n) 抖动。append 路径在日志热路径上，加锁临界区保持最小。

### D4：查询端点复用 base-path 占位符模式

`ArchimedesLogController`（core）用与 ApiController 相同的 `${archimedes.api.base-path:/archimedes}` 占位符映射；自身端点天然被 REST 扫描排除规则覆盖。响应为朴素 JSON（与 /apis 风格一致，不套 code/data 壳）：`{traceId,total,page,size,logs:[...]}`。`/trace/current` 返回 `{traceId: MDC[mdcKey]}` 供前端联动。

### D5：独立自动装配 `ArchimedesLogAutoConfiguration`

条件：`@ConditionalOnClass(ch.qos.logback.classic.LoggerContext)` + `@ConditionalOnWebApplication(SERVLET)` + `archimedes.log.capture.enabled`（matchIfMissing=true）。Bean：`LogStore`（`@ConditionalOnMissingBean`——ES 实现的让位点）、`LogCaptureInitializer`、`ArchimedesLogController`。依赖 `TraceProperties` 由 trace 装配提供；两装配无强序依赖（properties Bean 由 `@EnableConfigurationProperties` 幂等提供）。

### D6：logback 编译基线 = 1.2.x（SB2.7 BOM），运行兼容 1.5.x（SB3.3）

所用 API（`AppenderBase`、`ILoggingEvent#getTimeStamp/getLevel/getThreadName/getLoggerName/getFormattedMessage/getMDCPropertyMap`、`LoggerContext#getLogger`、`Logger#addAppender/detachAppender`）在 1.2 与 1.5 的 FQCN 与签名一致，与 core"最低公分母编译"策略同构；sb3 集成测试实测兜底。

## Risks / Trade-offs

- [异步 Appender 场景（用户配置 AsyncAppender 包 root 输出）] → 我们直接挂 root，与用户输出链并列，不受影响
- [日志风暴写满内存] → 双上限 + 整 trace 淘汰；默认 10000 条（粗估 <10MB）
- [多线程 append 与查询并发] → 存储内部单锁；查询拷贝快照后排序分页，不阻塞 append 长时间
- [logback 1.2/1.5 二进制漂移] → D6 API 面已核对 + 双端集成测试

## Migration Plan

1. core：模型 + SPI + 内存实现 + Appender + Initializer + 属性 + Controller + 单测
2. starter×2：自动装配 + 注册行 + 装配测试
3. UI 第三分区；双端 e2e（多线程日志归集查询）；全量构建 + 真机验证

## Open Questions

（无）
