# Proposal: ui-tabs-and-rest-debug

## Why

内置 UI 目前是四个纵向堆叠分区（REST / WebSocket / RPC / Trace Logs）+ 单个全局过滤输入框。随着四类 RPC 协议接入，单页信息量已明显过载：用户需要滚动寻找目标协议区，过滤能力只有一个纯文本匹配，且看到 REST 契约后无法直接发起调试请求（需求文档的"接口信息展示"闭环缺最后一步）。`docs/功能清单与任务列表.md` Slice 12 明确列出：按协议分 Tab、搜索与筛选、REST 在线调试、RPC 契约详情展示。

## What Changes

单文件重构 `archimedes-core/src/main/resources/archimedes-ui/index.html`（保持零构建、零外部依赖、ES5 风格）：

1. **Tab 化导航（12.1）**：页面改为 5 个 Tab —— REST / WebSocket / RPC / TR / 链路追踪。TR Tab 展示 `rpcApis` 中 `protocol ∈ {SOFA_TR, TRPC}` 的条目，RPC Tab 展示其余协议（DUBBO/GRPC 及未来协议），两者复用同一渲染函数。Tab 标签带条目计数，当前 Tab 记忆到 URL hash。
2. **搜索与筛选（12.2）**：保留全局文本过滤（作用于当前 Tab）；REST Tab 增加 HTTP 方法下拉筛选；RPC/TR Tab 增加协议 chip 筛选。计数展示"命中/总数"。
3. **REST 在线调试（12.3）**：REST 条目行内展开 try-it 面板——方法选择、路径变量、Query 参数、Header、请求体（按契约参数预填）→ `fetch` 发起同源请求 → 展示状态码、耗时、响应头、响应体（JSON 自动美化）。若响应头携带 traceId（名称含 "trace"，默认 `X-Trace-Id`），提供一键跳转链路追踪 Tab 并自动查询该 traceId 的日志——打通"调试 → 看链路日志"闭环。
4. **RPC 契约详情展示（12.4）**：RPC/TR 条目展示 `metadata` 键值对（如 SOFA 的 uniqueId/bindings、gRPC 的 streaming 形态），补齐契约详情。

**不变量**：`__ARCHIMEDES_API_URL__` 占位符注入契约不变；渲染后页面包含注入的 apis 地址；`ArchimedesApiController` 与 `/apis` JSON 结构零改动（纯前端变更）。

## Capabilities

### Modified
- `api-grouping`：UI 展示要求从"分区展示"升级为"Tab 分区 + 筛选 + REST 在线调试 + RPC 契约详情"。

## Impact

- 代码：仅 `archimedes-core/src/main/resources/archimedes-ui/index.html` 一个文件；双 starter 的 `EndToEndTest` UI 断言各补一条 Tab 结构断言。
- 兼容性：无 API/配置变更；UI 为整页替换，无宿主可依赖的旧 DOM 契约。
- 风险：try-it 发起的是浏览器同源请求，不新增服务端攻击面（等价于用户手动 curl）；页面对所有动态内容沿用 `esc()` 转义防注入。
