# rpc-scanning Specification

## Purpose

定义 RPC 类协议（Dubbo/gRPC/SOFARPC-TR/tRPC）的统一契约模型、rpcApis 分组约定与各协议扫描要求；四协议（Dubbo/gRPC/SOFARPC-TR/tRPC）已全部覆盖。

## Requirements

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

### Requirement: 扫描 gRPC 服务
宿主容器中存在 `io.grpc.BindableService` Bean（含 `@GrpcService` 等主流集成注册的服务实现）时，系统 SHALL 经 `bindService()` 提取服务名与每个方法的名称、gRPC 方法形态（UNARY/SERVER_STREAMING/CLIENT_STREAMING/BIDI_STREAMING，记入方法 metadata）及可解析时的请求/响应消息类型，以 `protocol=GRPC` 计入 `rpcApis`；不 SHALL 要求宿主注册 Server Reflection 或启动 gRPC Server。

#### Scenario: BindableService Bean 被扫描
- **WHEN** 宿主注册了一个 BindableService Bean（服务名 demo.Greeter，含 UNARY 方法 SayHello）并启动
- **THEN** `rpcApis` 中存在 `protocol=GRPC`、`serviceName=demo.Greeter` 的条目，其方法含 `SayHello` 且 metadata 标注 `grpcMethodType=UNARY`

#### Scenario: 无 gRPC 时零影响
- **WHEN** 宿主 classpath 无 grpc-api 并启动
- **THEN** 不注册 gRPC 扫描 Bean，其余功能不受影响

### Requirement: 扫描 SOFARPC 服务
宿主容器中存在 `@SofaService`（`com.alipay.sofa.runtime.api.annotation.SofaService`）注解的 Bean 时，系统 SHALL 提取服务接口（注解 interfaceType，未指定时回退 Bean 的唯一实现接口）、方法签名、uniqueId 与 bindings（binding 类型列表，记入服务 metadata），以 `protocol=SOFA_TR` 计入 `rpcApis`；实现 SHALL 不引入 SOFA 编译依赖（反射式读取），classpath 无该注解时 SHALL 零装配。

#### Scenario: @SofaService 服务被扫描
- **WHEN** 宿主以 `@SofaService(interfaceType=GreetingService.class, uniqueId="demo", bindings=@SofaServiceBinding(bindingType="tr"))` 注册服务并启动
- **THEN** `rpcApis` 中存在 `protocol=SOFA_TR`、serviceName 为该接口全限定名的条目，metadata 含 `uniqueId=demo` 与 `bindings=tr`，methods 含接口方法签名

#### Scenario: 无 SOFA 时零影响
- **WHEN** 宿主 classpath 无 @SofaService 注解类并启动
- **THEN** 不装配 SOFA 扫描 Bean，其余功能不受影响

### Requirement: 扫描腾讯 tRPC 服务
宿主容器中存在 `@TRpcService`（`com.tencent.trpc.spring.annotation.TRpcService`）注解的 Bean 时，系统 SHALL 提取服务接口（回退规则同 SOFA）与方法签名，防御式读取注解中实际存在的 name/version/group 等属性，以 `protocol=TRPC` 计入 `rpcApis`；classpath 无该注解时 SHALL 零装配。

#### Scenario: @TRpcService 服务被扫描
- **WHEN** 宿主以 `@TRpcService` 注解注册服务实现 Bean（实现唯一接口）并启动
- **THEN** `rpcApis` 中存在 `protocol=TRPC`、serviceName 为该接口全限定名的条目及其方法签名

#### Scenario: 无 tRPC 时零影响
- **WHEN** 宿主 classpath 无 @TRpcService 注解类并启动
- **THEN** 不装配 tRPC 扫描 Bean，其余功能不受影响
