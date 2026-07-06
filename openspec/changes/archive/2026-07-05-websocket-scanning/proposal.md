# Proposal: websocket-scanning

## Why

Archimedes 目前只扫描 REST 接口，需求要求覆盖 WebSocket 接口契约（`docs/项目需求.md` §三）。同时，多协议时代的 `/apis` 响应需要一个可扩展的分组结构——现在引入分组，是后续 RPC/TR 各协议接入的结构地基，越晚改破坏面越大。

## What Changes

- 新增 WebSocket 接口扫描，覆盖三种主流形态：
  - `@ServerEndpoint` 注解端点（javax/jakarta 包名分叉，扫描器分别落在 sb2/sb3 starter）
  - Spring `WebSocketConfigurer`/`WebSocketHandlerRegistry` 注册的 handler（含 SockJS 标记；`org.springframework.web.socket.*` 双版本包名一致，落 core）
  - STOMP：`@MessageMapping`/`@SubscribeMapping` 方法与目的地（`org.springframework.messaging.*` 包名一致，落 core）
- 新增统一贡献者接口 `WebSocketApiContributor`（core），各形态扫描器实现之，由 starter 条件装配
- **BREAKING**：`GET {base-path}/apis` 响应从扁平 REST 数组升级为分组对象 `{"restApis": [...], "webSocketApis": [...]}`
- 内置 UI 升级为按协议分组展示（REST / WebSocket 两组）
- 所有 WebSocket 依赖 `<optional>true</optional>` + `@ConditionalOnClass`：宿主未使用 WebSocket 时零 Bean、零行为差异
- 两个 example 应用各新增 WebSocket 样例端点

## Capabilities

### New Capabilities

- `websocket-scanning`: 三种形态的 WebSocket 端点扫描、数据模型、条件装配与零影响保证。
- `api-grouping`: `/apis` 的多协议分组响应结构（restApis / webSocketApis，后续协议按同模式扩展）。

### Modified Capabilities

（无——`spring-boot-2-support`/`spring-boot-3-support` 的既有 requirement 文本不受影响：其场景描述的"返回接口 JSON、UI 页面、可关闭"语义保持成立。）

## Impact

- **core**：新增 `model/WsApiInfo`、`scanner/ws/`（Spring handler 扫描器、STOMP 扫描器、`WebSocketApiContributor` SPI）；`ArchimedesApiController.apis()` 返回类型改为分组对象；新增 `spring-websocket`/`spring-messaging` optional 依赖（2.7 BOM 版本）
- **starter×2**：自动装配增加 WebSocket 条件 Bean；各新增 `@ServerEndpoint` 扫描器（javax/jakarta）
- **UI**：index.html 分组渲染
- **测试**：现有断言 `/apis` 为数组的测试全部随分组结构调整；新增双端 WebSocket 集成测试（需 `spring-boot-starter-websocket` test 依赖）
- **example / example-boot2**：新增 WebSocket 样例（原生 handler + @ServerEndpoint + STOMP 至少各一处，SB3 侧齐全，SB2 侧对称）
