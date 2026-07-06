# websocket-scanning Specification

## Purpose

定义 WebSocket 接口契约的扫描能力：@ServerEndpoint（javax/jakarta）、Spring WebSocketHandler 注册、STOMP 消息映射三种形态的端点发现、数据模型与零影响条件装配。

## Requirements

### Requirement: 扫描 @ServerEndpoint 注解端点
系统 SHALL 扫描 Spring 容器中标注了 `@ServerEndpoint`（SB2 侧 `javax.websocket.server.ServerEndpoint`，SB3 侧 `jakarta.websocket.server.ServerEndpoint`）的 Bean，提取端点路径与处理类，以 `kind=SERVER_ENDPOINT` 计入 WebSocket 契约。

#### Scenario: Bean 形式的 ServerEndpoint 被扫描
- **WHEN** 宿主应用将一个 `@ServerEndpoint("/ws/echo")` 类注册为 Spring Bean 并启动
- **THEN** `{base-path}/apis` 的 `webSocketApis` 中存在 `kind=SERVER_ENDPOINT`、`path=/ws/echo` 的条目

### Requirement: 扫描 Spring WebSocketHandler 注册端点
系统 SHALL 从 `SimpleUrlHandlerMapping` 中识别 `WebSocketHttpRequestHandler` 与 `SockJsHttpRequestHandler` 注册的端点，提取路径与 handler 类，以 `kind=HANDLER` 计入契约，SockJS 端点 SHALL 标记 `sockJs=true`。

#### Scenario: WebSocketConfigurer 注册的 handler 被扫描
- **WHEN** 宿主通过 `WebSocketConfigurer` 注册 `registry.addHandler(handler, "/ws/chat")` 并启动
- **THEN** `webSocketApis` 中存在 `kind=HANDLER`、`path=/ws/chat`、`handlerClass` 为该 handler 类名的条目

### Requirement: 扫描 STOMP 消息映射
系统 SHALL 通过 `SimpAnnotationMethodMessageHandler` 提取 `@MessageMapping` 与 `@SubscribeMapping` 方法及其目的地 pattern，分别以 `kind=STOMP_MESSAGE`、`kind=STOMP_SUBSCRIBE` 计入契约；STOMP 握手端点 SHALL 以 `kind=STOMP_ENDPOINT` 计入。

#### Scenario: MessageMapping 方法被扫描
- **WHEN** 宿主启用 `@EnableWebSocketMessageBroker` 且存在 `@MessageMapping("/hello")` 的控制器方法
- **THEN** `webSocketApis` 中存在 `kind=STOMP_MESSAGE`、`path=/hello`、`handlerMethod` 为该方法名的条目

### Requirement: 无 WebSocket 依赖时零影响
宿主 classpath 不存在对应 WebSocket 类型时，相关扫描器 Bean SHALL NOT 被注册，`webSocketApis` SHALL 为空数组，REST 扫描与端点行为不受任何影响。

#### Scenario: 纯 REST 应用不受影响
- **WHEN** 宿主未引入任何 WebSocket 依赖并启动
- **THEN** 上下文中不存在任何 `WebSocketApiContributor` Bean，`/apis` 正常返回且 `webSocketApis` 为 `[]`
