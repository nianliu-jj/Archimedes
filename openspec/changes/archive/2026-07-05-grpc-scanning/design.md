# Design: grpc-scanning

## Context

需求文档建议 gRPC 走 Server Reflection（注册 ProtoReflectionService 后经反射客户端查询）。但 Reflection 要求宿主显式注册反射服务、且需起真实 gRPC Server 与自连接查询——与"容器内自省、零配置"的项目基调相悖。Spring 生态中 gRPC 服务实现（含 `net.devh:grpc-server-spring-boot-starter` 的 `@GrpcService`）都是 `BindableService` Bean，`bindService()` 返回完整 `ServerServiceDefinition`——契约就在容器里。

## Goals / Non-Goals

**Goals:**
- gRPC 服务契约（服务名、方法、streaming 形态、消息类型）进入 `rpcApis`
- 宿主无 gRPC 时零影响；不要求 Server 启动或反射服务注册

**Non-Goals:**
- Server Reflection 协议对接（跨进程查询他方服务不在"自省本应用"范围）
- proto 字段级 schema 展开（消息类型名到顶）

## Decisions

### D1：扫 `BindableService` Bean 而非 Server Reflection

覆盖 net.devh/@GrpcService、LogNet 等主流集成与手动注册（它们的服务实现 Bean 均实现 BindableService）；`bindService()` 幂等无副作用（构建定义对象，不绑定网络）。API 面全部在 `io.grpc:grpc-api`（轻依赖）。

### D2：方法信息提取

`MethodDescriptor#getBareMethodName()`（1.33+，作为 fullMethodName 的截取兜底）、`getType()` → metadata `{grpcMethodType: UNARY|...}`；请求/响应类型经 `Marshaller instanceof PrototypeMarshaller` → `getMessagePrototype().getClass().getName()`（protobuf marshaller 均实现），非 proto marshaller 时留空数组/null——契约仍可展示方法与形态。

### D3：`RpcMethodInfo.metadata` 通用扩展位

协议特有信息（gRPC streaming 形态、后续 TR 的 uniqueId 等）统一进 `Map<String,String> metadata`，避免模型按协议膨胀；Dubbo 侧不填（null 不序列化为空对象，Jackson 默认输出 null——可接受，UI 判空）。

### D4：测试用手写 BindableService（零 protoc）

自建 `MethodDescriptor`（String marshaller）+ no-op `ServerCallHandler` 构造 `ServerServiceDefinition`——只依赖 grpc-api，验证的正是扫描器消费的真实 API 面；protoc 编译链（插件+生成代码）为测试引入的成本与脆弱性不成比例。PrototypeMarshaller 分支在单测以最小实现覆盖。

## Risks / Trade-offs

- [bindService() 由我们调用是否有副作用] → 定义构建为纯对象操作；每次 /apis 调用重建，成本微小（可后续加缓存，与 REST 扫描同策略）
- [非 protobuf marshaller（json 等）取不到消息类型] → 方法名+形态仍展示，类型留空，UI 正常
- [grpc-api 1.58 编译、宿主低版本运行] → 所用 API（BindableService/ServerServiceDefinition/MethodDescriptor.getType/getBareMethodName/PrototypeMarshaller）自 1.33 起稳定

## Migration Plan

1. core：metadata 字段 + GrpcRpcScanner + 单测
2. starter×2：嵌套配置 + 集成测试（手写 BindableService Bean → /apis 断言）
3. 全量构建 + 收尾

## Open Questions

（无）
