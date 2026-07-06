# Design: websocket-scanning

## Context

现状：core 的 `RestApiScanner` 扫 `RequestMappingHandlerMapping`，`ArchimedesApiController.apis()` 直接返回 `List<ApiInfo>`；UI 渲染扁平列表。WebSocket 在 Spring 生态有三种注册形态，其中 `@ServerEndpoint` 的注解类型存在 javax/jakarta 包名分叉（多模块拆分正是为此准备的）；另两种形态的 Spring API（`org.springframework.web.socket.*`、`org.springframework.messaging.*`）在 Spring 5.3 与 6.x 包名与所用方法签名一致，可单份落 core。

## Goals / Non-Goals

**Goals:**
- 三种 WebSocket 形态的端点契约进入 `/apis` 输出与 UI
- 分组 JSON 结构落地，为 RPC/TR 预留扩展位
- 宿主无 WebSocket 依赖时零影响（不新增任何 Bean）

**Non-Goals:**
- 不做 WebSocket 在线调试
- 不解析 `@OnMessage` 参数级契约（仅列出端点与处理方法）
- 不扫描非 Spring Bean 的 `@ServerEndpoint` 类（容器 SCI 直接注册、绕过 Spring 的场景）

## Decisions

### D1：贡献者 SPI —— `WebSocketApiContributor`

core 定义 `public interface WebSocketApiContributor { List<WsApiInfo> contribute(); }`。控制器聚合所有贡献者 Bean（可为空集合）。三种形态各自成为一个实现，条件装配决定谁在场。这个模式（模型 + 贡献者 + 条件装配）就是后续 RPC/TR 扫描器的模板。

**备选否决**：在 `RestApiScanner` 上扩展成"全协议扫描器"——上帝类，且每种协议的 `@ConditionalOnClass` 边界没法分开。

### D2：三个扫描器的归属与探测点

| 形态 | 归属 | 探测/数据源 |
|---|---|---|
| Spring handler | core | 遍历 `SimpleUrlHandlerMapping` Bean，识别 `WebSocketHttpRequestHandler`（取 `getWebSocketHandler()`）与 `SockJsHttpRequestHandler`（标记 sockJs），排除 STOMP 的 `SubProtocolWebSocketHandler`（归 STOMP 形态展示） |
| STOMP | core | `SimpAnnotationMethodMessageHandler.getHandlerMethods()` → `SimpMessageMappingInfo.getDestinationConditions().getPatterns()`；按方法注解区分 `STOMP_MESSAGE`/`STOMP_SUBSCRIBE`；同时从 STOMP 的 `SimpleUrlHandlerMapping` 提取握手端点 |
| `@ServerEndpoint` | 各 starter | `applicationContext.getBeansWithAnnotation(ServerEndpoint.class)`（javax/jakarta 各一份），path 取注解 value |

所用 Spring API（`getUrlMap`、`getWebSocketHandler`、`getHandlerMethods`、`getDestinationConditions().getPatterns()`）已核对在 5.3 与 6.x 均存在且签名一致——core 单份编译成立。

### D3：数据模型 `WsApiInfo`（core/model）

字段：`kind`（`SERVER_ENDPOINT`/`HANDLER`/`STOMP_ENDPOINT`/`STOMP_MESSAGE`/`STOMP_SUBSCRIBE`）、`path`（端点路径或 STOMP 目的地）、`handlerClass`、`handlerMethod`（类级形态为 null）、`sockJs`（boolean）。刻意保持扁平，Jackson 直接序列化。

### D4：分组响应 —— 专用聚合类型而非 Map

core 新增 `model/ApiCatalog`：`{ List<ApiInfo> restApis; List<WsApiInfo> webSocketApis; }`。控制器 `apis()` 返回 `ApiCatalog`。**BREAKING** 一次到位；后续协议在 `ApiCatalog` 上加字段即可（约定：无该协议时输出空数组而非缺字段，前端零判空）。

**备选否决**：`Map<String, List<?>>`——失去类型与文档性；版本化端点 `/apis/v2`——项目未发布，无兼容包袱，不值得背双端点。

### D5：条件装配拓扑

- core 的两个扫描器由**各 starter** 的自动装配注册（core 自身无自动装配）：
  - `@ConditionalOnClass(WebSocketHandler.class)` → Spring handler 扫描器
  - `@ConditionalOnClass(SimpAnnotationMethodMessageHandler.class)` → STOMP 扫描器
  - `@ConditionalOnClass((javax|jakarta).websocket.server.ServerEndpoint.class)` → 各 starter 自己的 ServerEndpoint 扫描器
- 控制器构造参数追加 `List<WebSocketApiContributor>`（Spring 注入空列表当无贡献者）
- core 新增 optional 依赖：`spring-websocket`、`spring-messaging`（2.7 BOM 版本，编译期用）；starter 侧同样 optional——依赖是否在场由宿主决定

### D6：UI 分组渲染

index.html 改为两个分区（REST APIs / WebSocket APIs），数据源从数组切到 `catalog.restApis`/`catalog.webSocketApis`；WebSocket 区按 kind 徽标展示。无 WebSocket 数据时该分区显示空态。

## Risks / Trade-offs

- [`@ServerEndpoint` 非 Bean 注册的端点扫不到] → Non-Goal 明示；Spring 场景的标准做法（`ServerEndpointExporter` + Bean）在覆盖内，README/UI 空态不误导
- [STOMP 目的地前缀（`/app` 等）与方法级 pattern 的拼接语义] → 直接展示方法级 pattern，并在 `STOMP_ENDPOINT` 条目展示握手端点，不做前缀推断（推断需读 broker registry 私有配置，脆弱）
- [分组 BREAKING 改动漏改既有断言] → 全量测试跑双端，凡断言 `/apis` 数组结构的用例集中在 3 个文件，逐一迁移
- [spring-websocket 5.3 编译的 core 扫描器运行于 6.x] → 与 REST 扫描器同一契约（D2 已核对 API 面）；sb3 集成测试实测兜底

## Migration Plan

1. core：模型 + SPI + 两个扫描器 + 控制器分组
2. starter×2：条件装配 + ServerEndpoint 扫描器 + 既有测试迁移
3. UI 分组渲染
4. 双端 WebSocket 集成测试 + example 样例
5. 全量 `mvn clean install`；example 真机验证

## Open Questions

（无——推荐方案已在提案阶段锁定。）
