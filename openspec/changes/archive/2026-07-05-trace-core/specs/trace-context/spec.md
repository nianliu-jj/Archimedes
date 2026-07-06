# Spec Delta: trace-context

## ADDED Requirements

### Requirement: 每个请求建立 traceId 上下文
启用 trace 时（默认启用），系统 SHALL 在每个 HTTP 请求进入时确定 traceId 并写入 MDC（key 可配置，默认 `traceId`），同时生成 spanId 写入 MDC（key 可配置，默认 `spanId`）；请求结束时 SHALL 仅清理本请求写入的 MDC 键，SHALL NOT 清除宿主写入的其它 MDC 键。

#### Scenario: 无上游 traceId 时自动生成
- **WHEN** 请求不带 traceId 请求头进入宿主应用
- **THEN** 业务代码内 `MDC.get("traceId")` 非空，响应头 `X-Trace-Id` 与之相同

#### Scenario: 请求结束精准清理
- **WHEN** 请求处理完成
- **THEN** 本请求写入的 traceId/spanId MDC 键被移除，宿主在请求前已存在的其它 MDC 键不受影响

### Requirement: traceId 来源解析链
traceId 的确定 SHALL 按以下优先级：用户 `TraceIdResolver` Bean → 配置的请求头（默认 `X-Trace-Id`）→ 宿主已写入的 MDC 值（仅当 `use-project-trace-id=true`）→ `TraceIdGenerator` 生成。

#### Scenario: 上游请求头透传
- **WHEN** 请求携带 `X-Trace-Id: abc123`
- **THEN** 该请求链路的 MDC traceId 为 `abc123`，响应头回写 `abc123`

#### Scenario: 用户自定义生成器
- **WHEN** 宿主定义了自己的 `TraceIdGenerator` Bean（如雪花算法）
- **THEN** 默认 UUID 生成器让位（`@ConditionalOnMissingBean`），生成的 traceId 来自用户实现

#### Scenario: 信任宿主自有 traceId
- **WHEN** `archimedes.trace.use-project-trace-id=true` 且宿主自有 Filter 已在 MDC 写入 traceId
- **THEN** 系统不覆盖、不清理宿主的 traceId，仅回写响应头

### Requirement: trace 配置面与开关
系统 SHALL 提供 `archimedes.trace.*` 配置：`enabled`（默认 true）、`use-project-trace-id`（默认 false）、`header-name`（默认 `X-Trace-Id`）、`response-header`（默认 true）、`mdc-key`（默认 `traceId`）、`span-id-key`（默认 `spanId`）；`enabled=false` 时 SHALL 不注册任何 trace 相关 Bean 与 Filter。

#### Scenario: 关闭 trace
- **WHEN** 配置 `archimedes.trace.enabled=false` 并启动
- **THEN** 上下文中无 TraceIdFilter 注册，请求的 MDC 中无 Archimedes 写入的 traceId

#### Scenario: 自定义 header 名
- **WHEN** 配置 `archimedes.trace.header-name=X-Request-Id` 且请求携带该头
- **THEN** MDC traceId 取自 `X-Request-Id`，响应头也回写到 `X-Request-Id`
