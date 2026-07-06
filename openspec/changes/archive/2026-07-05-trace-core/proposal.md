# Proposal: trace-core

## Why

全链路日志追踪是 Archimedes 三大支柱之二（`docs/项目需求.md` §三），一切日志采集/查询能力都以"每个请求有 traceId 且写入 MDC"为前提。本 slice 落地 trace 的核心闭环：请求进入 → 解析/生成 traceId → MDC → 响应头回写 → 请求结束精准清理；跨线程传递与日志采集由后续 slice 在此之上叠加。

## What Changes

- core 新增 `trace` 包（零 servlet 依赖）：
  - `TraceIdGenerator` SPI + 默认 UUID 实现（用户可用自定义 Bean 替换，`@ConditionalOnMissingBean`）
  - `TraceIdResolver` SPI（可选用户 Bean）：从请求中按自定义逻辑解析项目自有 traceId
  - `TraceRequest` 最小请求抽象（`getHeader`），隔离 servlet API
  - `TraceContextManager`：解析链（resolver → 请求头 → 宿主 MDC → 生成）、MDC 写入与**按 key 精准清理**（不做 `MDC.clear()`，不破坏宿主自有 MDC 上下文）
  - `TraceProperties`（`archimedes.trace.*`）：enabled / use-project-trace-id / header-name / response-header / mdc-key / span-id-key
- 各 starter 新增 `TraceIdFilter`（javax/jakarta 各一份薄适配）+ 独立自动装配 `ArchimedesTraceAutoConfiguration`（最高优先级 Filter 注册，`archimedes.trace.enabled=false` 时整体不装配）
- spanId：每请求生成并写入 MDC（key 可配）
- 两个 example 各加一个演示端点（返回当前请求 traceId）

## Capabilities

### New Capabilities

- `trace-context`: traceId 的解析/生成/传播闭环——SPI 可插拔、MDC 写入与精准清理、响应头回写、`archimedes.trace.*` 配置面。

### Modified Capabilities

（无）

## Impact

- **core**：新增 `trace/` 包与配置属性；无新第三方依赖（slf4j-api 已有）
- **starter×2**：新增 Filter 与 trace 自动装配类；注册文件各加一行
- **测试**：core 单测（解析链矩阵、清理语义）；双 starter 集成测试（透传/生成/自定义 Bean/关闭开关/自定义头名）
- **example×2**：演示端点
- 与 REST/WS 扫描完全正交，`/apis` 行为不变
