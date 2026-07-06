# Design: webflux-rest-scanning

## Context

servlet 与 reactive 的 `RequestMappingHandlerMapping` 是两个包下的平行类（`web.servlet.mvc.method.annotation` vs `web.reactive.result.method.annotation`），`RequestMappingInfo` 亦然，但形状相同：`getHandlerMethods()` 均返回 `Map<RequestMappingInfo, HandlerMethod>`，且 `HandlerMethod` 是共享类（spring-web）。参数提取、排除规则、缓存等逻辑与栈无关。`ArchimedesApiController` 纯注解式（`@RestController` + `ResponseEntity`），WebFlux 注解模型原样支持。

## Goals / Non-Goals

- Goals：REACTIVE 宿主下 `/apis` + UI 可用；REST 契约扫描覆盖响应式 `@RestController`；RPC 四协议扫描在响应式宿主同等生效；SB2/SB3 双端一致。
- Non-Goals：响应式栈的链路追踪与日志采集（需 Reactor Context 传播，机制与 MDC 快照根本不同，后续独立 Slice）；reactive WebSocket/RSocket 扫描；函数式路由（RouterFunction）扫描——注解模型优先，函数式端点无统一契约元数据，暂不支持。

## Decisions

### D1：抽象形态 = 接口 + 抽象骨架，servlet 扫描器公共 API 不变
`RestApiContributor`（接口，`scan()`）+ `AbstractRestApiScanner`（缓存模板、`buildApiInfo(HandlerMethod, paths, methods, consumes, produces)`、参数提取、排除规则、排序）。`RestApiScanner extends AbstractRestApiScanner`：构造器 `(List<servlet RMHM>, props)`、静态 `extractPaths` 保持原位（core 测试直接引用）。`ReactiveRestApiScanner` 为响应式孪生，路径取自 reactive `PatternsRequestCondition.getPatterns()`（`Set<PathPattern>`，reactive 栈无 AntPathMatcher 回退分支）。
`ArchimedesApiController` 字段与构造参数放宽为 `RestApiContributor`——所有既有调用点（传 `RestApiScanner`）源码兼容。

### D2：core 增加 spring-webflux optional 依赖
与 spring-webmvc 同待遇：BOM 管版本（SB2.7 → 5.3.x），optional 不传递，宿主没有 webflux 时零影响。编译面 API（reactive RequestMappingInfo/PatternsRequestCondition）在 5.3 与 6.x 一致，单 jar 双端复用成立。

### D3：REACTIVE 分支为独立自动装配类，RPC 嵌套配置抽共享
`ArchimedesReactiveAutoConfiguration`：`@ConditionalOnWebApplication(type = REACTIVE)` + `@ConditionalOnClass`(reactive RMHM) + `archimedes.api.enabled` 门控；注册 `archimedesReactiveRestApiScanner` 与 `archimedesApiController`（bean 名与 SERVLET 分支相同——两分支条件互斥，运行时不共存）。
四类 RPC 扫描配置（Dubbo/gRPC/SOFA-TR/tRPC，容器自省、栈无关）从 SERVLET 配置的嵌套类抽为 starter 内共享类 `RpcScanConfigurations`（普通 `@Configuration` 嵌套类容器），SERVLET 与 REACTIVE 自动装配各自 `@Import`——避免 4×2 份复制，条件注解随类走、语义不变。WebSocket 嵌套配置依赖 servlet WebSocket 栈，仅保留在 SERVLET 分支。
boot2 用经典 `@Configuration + @AutoConfigureAfter(WebFluxAutoConfiguration)` + spring.factories；boot3 用 `@AutoConfiguration(afterName = ...WebFluxAutoConfiguration)` + imports 文件追加。

### D4：trace/log 维持 SERVLET 门控（记录边界）
响应式请求处理跨事件循环线程，MDC 快照传递模型不适用；正确方案是 Reactor Context + （Boot 3.2+）`Hooks.enableAutomaticContextPropagation()` 一族，属独立工作量。本 Slice 不注册 reactive WebFilter，README 版本矩阵旁明确"链路追踪与日志查询当前仅 Servlet 栈"。

### D5：测试策略 = 真实注册表单测 + 上下文条件单测 + 强制 reactive 真服务 e2e
- core：`ReactiveRestApiScannerTest` 用真实 reactive `RequestMappingHandlerMapping` 实例 + `registerMapping(...)` 驱动（无需启动服务器），断言路径/方法/参数/返回类型提取与 base-path 排除。
- starter ×2：`ArchimedesReactiveAutoConfigurationTest` 用 `ReactiveWebApplicationContextRunner` 断言装配（scanner + controller + RPC contributor），`ApplicationContextRunner`（非 web）断言让位；`ReactiveEndToEndTest` 用 `@SpringBootTest(RANDOM_PORT, properties = "spring.main.web-application-type=reactive")` 起真实 Netty 服务，`TestRestTemplate` 断言 `/archimedes/apis` 含 `Mono`/`Flux` 返回类型的响应式端点、自身端点排除、UI 页可达（Boot 的测试加载器显式绑定 `spring.main.web-application-type`，双栈类路径下可强制 reactive）。
- starter pom 增加 `spring-boot-starter-webflux`（test scope）与 `spring-webflux`（optional，编译自动装配类）。

## Risks / Trade-offs

- 强制 reactive 的 e2e 与纯 reactive 类路径仍有差异（servlet jar 在类路径上）——用 FilteredClassLoader 变体补"隐藏 servlet 类仍正常装配 REACTIVE 分支"的用例覆盖；不再新增 example-webflux 模块（维护成本 > 增量置信度，Netty 真服务 e2e 已是真 HTTP）。
- `archimedesApiController` 双分支同名 bean：依赖条件互斥保证唯一。若未来出现双栈共存形态（Boot 不支持），装配会显式冲突报错而非静默错乱，可接受。

## Migration Plan

纯新增 + 内部重构，SERVLET 宿主无感知。响应式宿主升级依赖后自动获得 `/apis` + UI。

## Open Questions

无。
