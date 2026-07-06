# Tasks: grpc-scanning

## 1. core

- [x] 1.1 `RpcMethodInfo` 增加可选 metadata（Map<String,String>）
- [x] 1.2 `scanner/rpc/GrpcRpcScanner`（BindableService → ServerServiceDefinition，bare 方法名、形态 metadata、PrototypeMarshaller 类型解析）
- [x] 1.3 core pom：grpc-api optional（grpc.version 父属性）
- [x] 1.4 core 单测（手写 MethodDescriptor/ServerServiceDefinition 与 PrototypeMarshaller 分支）

## 2. starter 与测试

- [x] 2.1 双 starter 嵌套 GrpcScanConfiguration（ConditionalOnClass BindableService）+ grpc-api optional
- [x] 2.2 双端集成测试（手写 BindableService Bean → /apis rpcApis 断言 GRPC 条目）
- [x] 2.3 全量构建全绿

## 3. 收尾

- [x] 3.1 README gRPC 条目 + 功能清单勾选 Slice 9 + spec 同步（rpc-scanning ADDED）+ 归档 + 提交
