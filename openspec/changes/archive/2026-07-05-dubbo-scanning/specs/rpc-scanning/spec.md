# Spec Delta: rpc-scanning

## ADDED Requirements

### Requirement: 通用 RPC 契约模型
RPC 类协议的契约 SHALL 以统一模型表达：`protocol`（DUBBO/GRPC/SOFA_TR/TRPC）、`serviceName`（接口/服务全限定名）、`version`、`group`、`methods`（方法名、参数类型列表、返回类型）；所有 RPC 协议 SHALL 汇入 `/apis` 分组结构的同一 `rpcApis` 字段。

#### Scenario: rpcApis 字段始终存在
- **WHEN** 宿主无任何 RPC 框架并请求 `/apis`
- **THEN** `rpcApis` 字段存在且为空数组

### Requirement: 扫描 Dubbo provider 服务
宿主注册了 Dubbo provider（`@DubboService` 注解或 XML 方式，容器中存在 `ServiceBean`）时，系统 SHALL 提取每个服务的接口全限定名、version、group 与接口全部业务方法的签名，以 `protocol=DUBBO` 计入 `rpcApis`。

#### Scenario: @DubboService 服务被扫描
- **WHEN** 宿主以 `@DubboService(version="1.0.0", group="demo")` 暴露 `GreetingService` 接口并启动
- **THEN** `rpcApis` 中存在 `protocol=DUBBO`、`serviceName` 为该接口全限定名、`version=1.0.0`、`group=demo` 的条目，其 `methods` 含接口方法的名称、参数类型与返回类型

#### Scenario: 无 Dubbo 时零影响
- **WHEN** 宿主 classpath 无 Dubbo 并启动
- **THEN** 不注册任何 Dubbo 扫描 Bean，`/apis` 及其余功能不受影响

### Requirement: UI 展示 RPC 契约
内置 UI SHALL 提供 RPC APIs 分区：protocol 徽标、服务名、version/group 与方法签名列表；无数据时空态展示。

#### Scenario: Dubbo 服务在 UI 可见
- **WHEN** 宿主暴露 Dubbo 服务并打开 UI
- **THEN** RPC 分区列出该服务及其方法签名
