# Tasks: websocket-scanning

## 1. core 基础

- [x] 1.1 core pom 新增 optional 依赖：`spring-websocket`、`spring-messaging`（2.7 BOM 版本）
- [x] 1.2 新增 `model/WsApiInfo`（kind/path/handlerClass/handlerMethod/sockJs）与 `model/ApiCatalog`（restApis/webSocketApis，空数组约定）
- [x] 1.3 新增 `scanner/ws/WebSocketApiContributor` SPI 接口
- [x] 1.4 实现 `scanner/ws/SpringWebSocketHandlerScanner`（SimpleUrlHandlerMapping → WebSocketHttpRequestHandler/SockJsHttpRequestHandler，排除 SubProtocolWebSocketHandler）
- [x] 1.5 实现 `scanner/ws/StompMappingScanner`（SimpAnnotationMethodMessageHandler → MESSAGE/SUBSCRIBE 目的地；STOMP 握手端点 → STOMP_ENDPOINT）
- [x] 1.6 `ArchimedesApiController` 构造注入 `List<WebSocketApiContributor>`，`apis()` 返回 `ApiCatalog`

## 2. starter 装配

- [x] 2.1 sb3-starter：自动装配注册两个 core 扫描器（`@ConditionalOnClass` 分别守卫）+ 新增 `Boot3ServerEndpointScanner`（jakarta）+ optional 依赖
- [x] 2.2 sb2-starter：同样装配 + `Boot2ServerEndpointScanner`（javax）+ optional 依赖
- [x] 2.3 既有断言 `/apis` 为扁平数组的测试全部迁移到 `restApis` 分组读取（core 控制器测试 + 双端 EndToEnd）

## 3. UI 分组

- [x] 3.1 index.html 升级：restApis/webSocketApis 双分区渲染、kind 徽标、空态展示

## 4. 测试与样例

- [x] 4.1 core：两个扫描器的单元测试（构造 HandlerMapping/MessageHandler 直测，不起容器）
- [x] 4.2 sb3-starter：WebSocket 集成测试（starter-websocket test 依赖；覆盖 @ServerEndpoint jakarta + handler + STOMP + 纯 REST 应用 webSocketApis 为空）
- [x] 4.3 sb2-starter：镜像集成测试（javax）
- [x] 4.4 example：新增 WebSocket 样例（handler + @ServerEndpoint + STOMP）；example-boot2：对称样例（javax）
- [x] 4.5 全量 `mvn clean install` 全绿；example 真机启动验证 webSocketApis 输出与 UI 分组

## 5. 收尾

- [x] 5.1 README 功能说明更新（WebSocket 支持范围与 Non-Goal 边界）
- [x] 5.2 功能清单文档状态刷新（Slice 3 勾选）
