# Spec Delta: rpc-scanning

## ADDED Requirements

### Requirement: 扫描 gRPC 服务
宿主容器中存在 `io.grpc.BindableService` Bean（含 `@GrpcService` 等主流集成注册的服务实现）时，系统 SHALL 经 `bindService()` 提取服务名与每个方法的名称、gRPC 方法形态（UNARY/SERVER_STREAMING/CLIENT_STREAMING/BIDI_STREAMING，记入方法 metadata）及可解析时的请求/响应消息类型，以 `protocol=GRPC` 计入 `rpcApis`；不 SHALL 要求宿主注册 Server Reflection 或启动 gRPC Server。

#### Scenario: BindableService Bean 被扫描
- **WHEN** 宿主注册了一个 BindableService Bean（服务名 demo.Greeter，含 UNARY 方法 SayHello）并启动
- **THEN** `rpcApis` 中存在 `protocol=GRPC`、`serviceName=demo.Greeter` 的条目，其方法含 `SayHello` 且 metadata 标注 `grpcMethodType=UNARY`

#### Scenario: 无 gRPC 时零影响
- **WHEN** 宿主 classpath 无 grpc-api 并启动
- **THEN** 不注册 gRPC 扫描 Bean，其余功能不受影响
