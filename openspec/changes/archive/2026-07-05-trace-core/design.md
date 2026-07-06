# Design: trace-core

## Context

宿主项目形态多样：有的完全没有 traceId 体系（需要我们生成），有的有上游网关注入的请求头（需要透传），有的已有自建 Filter 往 MDC 写自有 traceId（绝不能覆盖或误清）。core 无 servlet 依赖的约束（modular-artifacts spec）要求 trace 核心逻辑与 Filter 壳分离。

## Goals / Non-Goals

**Goals:**
- 每个 HTTP 请求结束时，本请求写入的 MDC 键被精准清理；宿主自有 MDC 键零破坏
- traceId 来源可插拔且有明确优先级；响应头回写方便前端联动
- `archimedes.trace.*` 全配置面；`enabled=false` 时零 Bean

**Non-Goals:**
- 跨线程传递（Slice 5）
- 日志采集与查询（Slice 6）
- 分布式传播协议（W3C traceparent / B3）的解析兼容——只按配置的单一 header 名读写

## Decisions

### D1：traceId 解析链与优先级

```
TraceIdResolver Bean（用户提供，最高优先）
   ↓ 空/无 Bean
请求头 properties.headerName（默认 X-Trace-Id，上游透传）
   ↓ 空
宿主 MDC[mdcKey]（仅当 use-project-trace-id=true：宿主自有 Filter 已写入）
   ↓ 空
TraceIdGenerator.generate()（默认 UUID 去横线；@ConditionalOnMissingBean 可替换）
```

`use-project-trace-id` 的语义收敛为"信任宿主已写入的 MDC"：true 且宿主 MDC 已有值时，本请求视为宿主管理——不写不清，仅回写响应头。需求文档中该配置的意图（用户自有 traceId 体系）由 resolver/generator SPI + 该开关共同覆盖。

### D2：核心逻辑下沉 core，Filter 是 10 行薄壳

core 的 `TraceContextManager.begin(TraceRequest)` 返回 `TraceScope`（记录本请求实际写入的 MDC 键集合与 traceId），`TraceScope.close()` 只删除自己写入的键。**否决 `MDC.clear()`**（需求文档示例的做法）：会清掉宿主放入 MDC 的用户身份、租户等无关键，属于破坏性副作用。

`TraceRequest` 是 `String getHeader(String name)` 单方法接口——servlet 差异被压缩到 starter 里的一行 lambda：`manager.begin(request::getHeader)`。

### D3：Filter 注册与顺序

各 starter 用 `FilterRegistrationBean` 注册，order = `Ordered.HIGHEST_PRECEDENCE`：trace 上下文必须先于一切业务 Filter 建立，日志采集（Slice 6）才能覆盖全请求周期。URL pattern `/*`。

### D4：独立自动装配类 `ArchimedesTraceAutoConfiguration`

与 API 扫描装配分离：开关独立（`archimedes.trace.enabled` vs `archimedes.api.enabled`），关 API 展示不应关 trace，反之亦然。注册文件（imports / spring.factories）各加一行。条件：`@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnProperty(archimedes.trace.enabled, matchIfMissing=true)`。

### D5：spanId

每请求生成 16 位 hex（UUID 截断）写入 `spanIdKey`；与 traceId 同属一个 `TraceScope` 管理。本 slice 不做父子 span 层级（那是 APM 的领地，超出"日志链路查询"的需求范围）。

## Risks / Trade-offs

- [响应头在 `chain.doFilter` 前写入，若下游重置响应可能丢失] → 在 begin 后立即 `setHeader`，Servlet 规范下 committed 前可覆盖；集成测试断言头存在
- [异步 Servlet（`DispatcherType.ASYNC`）二次进入 Filter] → 注册时仅 `DispatcherType.REQUEST`，异步链路交由 Slice 5 的跨线程机制处理
- [宿主也有同名 header 的其它语义] → header 名可配置，文档说明

## Migration Plan

1. core：`trace` 包 + 属性 + 单测
2. starter×2：Filter + 自动装配 + 注册行 + 集成测试
3. example×2：演示端点；全量构建 + 真机验证

## Open Questions

（无）
