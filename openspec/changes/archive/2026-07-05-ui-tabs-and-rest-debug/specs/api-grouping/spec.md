# api-grouping 变更（delta）

## MODIFIED Requirements

### Requirement: UI 按协议 Tab 分区展示
内置 UI SHALL 以 Tab 导航按协议分区渲染接口列表：REST / WebSocket / RPC / TR / 链路追踪五个 Tab。TR Tab SHALL 展示 `rpcApis` 中 `protocol` 为 `SOFA_TR` 或 `TRPC` 的条目，RPC Tab SHALL 展示其余协议条目；WebSocket 条目 SHALL 展示 kind 标记，RPC/TR 条目 SHALL 展示 protocol 标记与 `metadata` 键值详情（metadata 为空时不渲染）；某协议无数据时 SHALL 展示空态而非隐藏错误；Tab 标签 SHALL 展示条目计数。

#### Scenario: Tab 分区渲染
- **WHEN** 宿主同时存在 REST 与 WebSocket 端点并打开 `{base-path}` 页面
- **THEN** 页面呈现 Tab 导航，REST 与 WebSocket Tab 分别列出对应条目且标签带计数

#### Scenario: TR 与 RPC 按协议二分
- **WHEN** `rpcApis` 同时含 DUBBO 与 SOFA_TR 条目
- **THEN** DUBBO 条目出现在 RPC Tab，SOFA_TR 条目出现在 TR Tab，且各自展示 protocol 标记

#### Scenario: metadata 详情展示
- **WHEN** 某 RPC 条目携带非空 `metadata`（如 uniqueId/bindings）
- **THEN** 该条目在列表中渲染 metadata 键值对

## ADDED Requirements

### Requirement: UI 搜索与筛选
内置 UI SHALL 提供作用于当前 Tab 的全局文本过滤；REST Tab SHALL 额外提供 HTTP 方法筛选，RPC/TR Tab SHALL 额外提供协议筛选；筛选后 SHALL 展示"命中数/总数"计数。

#### Scenario: 文本过滤当前 Tab
- **WHEN** 在 REST Tab 输入路径片段
- **THEN** REST 列表仅保留匹配条目且计数更新为 命中/总数

#### Scenario: REST 方法筛选
- **WHEN** 在 REST Tab 选择方法 POST
- **THEN** 列表仅保留含 POST 的条目

### Requirement: REST 在线调试
内置 UI 的 REST 条目 SHALL 支持行内展开调试面板：按契约预填方法、路径变量、Query 参数、Header 与请求体（BODY 参数默认 `application/json`），经浏览器同源 `fetch` 发起请求，并 SHALL 展示响应状态码、耗时、响应头与响应体（JSON 响应美化展示）；当响应头携带 traceId（头名含 `trace`，大小写不敏感）时 SHALL 提供跳转链路追踪 Tab 并自动查询该 traceId 日志的入口。

#### Scenario: 发起调试请求
- **WHEN** 展开某 GET 端点的调试面板并点击发送
- **THEN** 面板展示该请求的状态码、耗时、响应头与响应体

#### Scenario: traceId 联动查询
- **WHEN** 调试响应头含 `X-Trace-Id`
- **THEN** 面板提供入口，点击后切换到链路追踪 Tab 并自动查询该 traceId 的日志
