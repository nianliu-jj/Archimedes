# Proposal: webflux-rest-scanning

## Why

当前全部自动装配以 `@ConditionalOnWebApplication(type = SERVLET)` 收口，WebFlux（响应式栈）宿主引入依赖后完全无感——`/apis` 端点与 UI 均不生效。需求文档要求"引入即用"覆盖 Spring 主流形态，`docs/功能清单与任务列表.md` Slice 13 明确列出：spring-webflux 的 `@RestController` 扫描（响应式栈条件装配）。

## What Changes

1. **core 抽象重构**：抽出 `RestApiContributor` 接口（`List<ApiInfo> scan()`）与 `AbstractRestApiScanner` 骨架（缓存、参数/返回类型提取、排除规则、排序——这些与 Web 栈无关）；现有 `RestApiScanner`（servlet）公共 API 不变，改为继承骨架。
2. **core 新增响应式扫描器**：`ReactiveRestApiScanner` 消费 `org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping`（该 API 在 Spring 5.3 与 6.x 一致，reactive 栈始终使用 PathPattern）；core 增加 `spring-webflux` optional 依赖。
3. **controller 解耦**：`ArchimedesApiController` 构造参数从 `RestApiScanner` 放宽为 `RestApiContributor`（controller 本身纯注解式、零 servlet 依赖，在 WebFlux 下原样可用）。
4. **双 starter 增加 REACTIVE 条件装配分支**：`ArchimedesReactiveAutoConfiguration`（`@ConditionalOnWebApplication(type = REACTIVE)` + `@ConditionalOnClass(reactive RequestMappingHandlerMapping)`），注册响应式扫描器 + 复用 ArchimedesApiController；四类 RPC 扫描器（容器自省、与 Web 栈无关）抽为共享配置类，SERVLET 与 REACTIVE 分支同等装配；WebSocket 扫描（servlet 专属）仅保留在 SERVLET 分支。
5. **范围界定**：本 Slice 只做契约扫描与展示；链路追踪与日志采集在响应式栈上需要 Reactor Context 传播方案（与 MDC 快照传递机制根本不同），保持 SERVLET 门控并在 README 记录边界。

## Capabilities

### New
- `webflux-support`：响应式栈下的 REST 契约扫描与端点/UI 装配要求。

## Impact

- 代码：core（接口 + 骨架 + 响应式扫描器 + controller 参数放宽 + pom optional 依赖）；双 starter（新自动装配类 + RPC 共享配置抽取 + 注册文件 + pom optional/test 依赖）。
- 兼容性：`RestApiScanner` 公共 API 不变；`ArchimedesApiController` 构造签名放宽为父接口（源码兼容）；SERVLET 宿主行为零变化。
- 风险：强制 reactive 的测试环境同时存在 servlet 依赖——用 `spring.main.web-application-type=reactive` 显式指定（Boot 测试加载器原生支持）；纯 reactive 类路径以 FilteredClassLoader 用例覆盖。
