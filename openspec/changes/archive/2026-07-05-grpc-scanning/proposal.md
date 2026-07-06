# Proposal: grpc-scanning

## Why

gRPC 是既定四 RPC 协议第二站（`docs/项目需求.md` §4.2）。通用 RPC 模型（Slice 8）已就位，本 slice 只需一个扫描器与条件装配。

## What Changes

- core 新增 `scanner/rpc/GrpcRpcScanner`：扫描容器中的 `io.grpc.BindableService` Bean（`net.devh` 等主流 grpc-spring-boot 集成的 `@GrpcService` Bean 即此类型，手动注册的服务实现同样覆盖），经 `bindService()` 读取 `ServerServiceDefinition`：
  - serviceName = ServiceDescriptor 名（proto 包.服务名）
  - 每个 `MethodDescriptor` → 方法名（bare name）、请求/响应消息类型（经 `PrototypeMarshaller` 可解析时），gRPC 方法形态（UNARY/SERVER_STREAMING/CLIENT_STREAMING/BIDI_STREAMING）记入方法 metadata
- `RpcMethodInfo` 增加可选 `metadata` 字段（Map，协议特有信息；Dubbo 不填）
- core pom 增加 `io.grpc:grpc-api` optional（仅 API 面：BindableService/ServerServiceDefinition/MethodDescriptor，版本 `grpc.version=1.58.0` 属性管理）
- 双 starter 嵌套 `GrpcScanConfiguration`（`@ConditionalOnClass(BindableService.class)`）
- 集成测试用**手写 BindableService**（自建 MethodDescriptor + no-op handler，仅依赖 grpc-api）——不引入 protoc 编译链，轻量验证真实扫描路径
- 不需要启动 gRPC Server：扫描是容器内 Bean 自省（与 Server Reflection 方案相比零运行时耦合，设计取舍记录于 design）

## Capabilities

### New Capabilities

（无——gRPC 属 `rpc-scanning` 能力的扩展）

### Modified Capabilities

- `rpc-scanning`: 新增 gRPC 扫描 Requirement（BindableService Bean 自省）。

## Impact

- **core**：一个扫描器 + grpc-api optional + RpcMethodInfo.metadata 字段
- **starter×2**：各一个嵌套配置；测试仅 grpc-api（test scope）
- **UI**：无改动（RPC 分区通吃，GRPC 徽标自动出现）
