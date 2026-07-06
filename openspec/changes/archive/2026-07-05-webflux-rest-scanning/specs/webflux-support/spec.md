# webflux-support Specification

## ADDED Requirements

### Requirement: 响应式栈 REST 契约扫描
当宿主为 REACTIVE Web 应用且类路径存在 spring-webflux 时，依赖 SHALL 自动装配响应式 REST 扫描器，从响应式 `RequestMappingHandlerMapping` 提取注解式 `@RestController` 契约（路径、HTTP 方法、参数及来源、返回类型、consumes/produces、Deprecated 标记），语义与 Servlet 栈扫描一致；自身端点（`{base-path}/**`）SHALL 被排除；`base-packages` 过滤 SHALL 同等生效。

#### Scenario: 响应式端点在列
- **WHEN** REACTIVE 宿主定义返回 `Mono`/`Flux` 的 `@RestController` 并请求 `{base-path}/apis`
- **THEN** `restApis` 包含该端点条目，返回类型为对应的响应式类型签名

#### Scenario: 自身端点排除
- **WHEN** REACTIVE 宿主请求 `{base-path}/apis`
- **THEN** 结果不含 `{base-path}` 与 `{base-path}/apis` 自身

### Requirement: 响应式栈端点与 UI 装配
REACTIVE 宿主下 `GET {base-path}/apis`（分组 JSON）与 `GET {base-path}`（内置 UI，占位符注入）SHALL 与 Servlet 栈行为一致；`archimedes.api.enabled=false` SHALL 整体关闭；四类 RPC 扫描器（容器自省，与 Web 栈无关）SHALL 在 REACTIVE 分支同等条件装配；SERVLET 宿主行为 SHALL 零变化。

#### Scenario: 响应式宿主引入即用
- **WHEN** WebFlux 宿主仅引入 starter 不加任何配置
- **THEN** `{base-path}/apis` 返回分组 JSON 且 UI 页面可访问

#### Scenario: 非 REACTIVE 环境让位
- **WHEN** 应用为 SERVLET 或非 Web 环境
- **THEN** 响应式分支不装配任何 Bean

### Requirement: 响应式栈能力边界声明
链路追踪（traceId Filter/MDC）与日志采集查询当前 SHALL 保持 Servlet 栈门控，不在 REACTIVE 宿主注册；该边界 SHALL 在 README 中向用户明示。

#### Scenario: 响应式宿主无 trace 装配
- **WHEN** REACTIVE 宿主启动
- **THEN** 不注册 trace Filter 与日志查询端点，且无报错
