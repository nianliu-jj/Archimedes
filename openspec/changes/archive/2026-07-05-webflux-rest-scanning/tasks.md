# Tasks: webflux-rest-scanning

- [x] 1. core：`RestApiContributor` 接口 + `AbstractRestApiScanner` 骨架抽取；`RestApiScanner` 改继承（公共 API 不变）；`ArchimedesApiController` 构造参数放宽为接口；pom 增 spring-webflux optional
- [x] 2. core：`ReactiveRestApiScanner`（reactive RequestMappingHandlerMapping → PathPattern 路径提取）+ `ReactiveRestApiScannerTest`（registerMapping 驱动，含排除规则）
- [x] 3. starter ×2：`RpcScanConfigurations` 共享抽取（Dubbo/gRPC/SOFA-TR/tRPC 四嵌套配置），SERVLET 自动装配改 `@Import`，行为不变
- [x] 4. starter ×2：`ArchimedesReactiveAutoConfiguration`（REACTIVE 门控 + 响应式扫描器 + controller + RPC `@Import`）+ 注册文件追加 + pom（spring-webflux optional、spring-boot-starter-webflux test）
- [x] 5. starter ×2：`ArchimedesReactiveAutoConfigurationTest`（ReactiveWebApplicationContextRunner 装配/让位/隐藏 servlet 类）+ `ReactiveEndToEndTest`（强制 reactive 真 Netty 服务：restApis 含 Mono 端点、自身排除、UI 可达、端点真实可请求）
- [x] 6. `mvn clean install` 全绿（143 测试：core 63 + sb2 40 + sb3 40）；README 版本矩阵/边界说明更新
- [x] 7. 功能清单更新（Slice 13 勾选、状态、日期）
