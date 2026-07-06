# Spec Delta: api-grouping

## MODIFIED Requirements

### Requirement: /apis 返回多协议分组结构
`GET {base-path}/apis` SHALL 返回分组 JSON 对象，当前包含 `restApis`（REST 契约数组）、`webSocketApis`（WebSocket 契约数组）与 `rpcApis`（RPC 契约数组，四类 RPC 协议以 protocol 字段区分）三个字段；协议在宿主中不存在时对应字段 SHALL 为空数组而非缺失。后续新协议 SHALL 优先并入既有字段（RPC 类并入 `rpcApis`），仅在语义不同时新增字段。

#### Scenario: 分组结构返回
- **WHEN** 请求 `{base-path}/apis`
- **THEN** 响应为 JSON 对象且同时含 `restApis`、`webSocketApis` 与 `rpcApis` 字段，REST 条目语义与分组前一致

#### Scenario: 无 WebSocket 时字段仍在
- **WHEN** 纯 REST 宿主请求 `{base-path}/apis`
- **THEN** `webSocketApis` 字段存在且为 `[]`

### Requirement: UI 按协议分组展示
内置 UI SHALL 按协议分组渲染接口列表（REST / WebSocket / RPC 分区），WebSocket 条目 SHALL 展示 kind 标记，RPC 条目 SHALL 展示 protocol 标记；某协议无数据时 SHALL 展示空态而非隐藏错误。

#### Scenario: UI 双分区渲染
- **WHEN** 宿主同时存在 REST 与 WebSocket 端点并打开 `{base-path}` 页面
- **THEN** 页面分别在 REST 与 WebSocket 分区列出对应条目
