# Tasks: tr-scanning

## 1. core

- [x] 1.1 `RpcApiInfo` 增加服务级 metadata（Map<String,String>，可 null）
- [x] 1.2 `scanner/rpc/AnnotatedRpcScannerSupport`（反射注解扫描共用逻辑：forName、Bean 收集、serviceName 解析链、方法反射、每 Bean 异常隔离）
- [x] 1.3 `SofaTrRpcScanner`（interfaceType/uniqueId/bindings → SOFA_TR）与 `TrpcRpcScanner`（防御式属性 → TRPC）
- [x] 1.4 core test 源码定义同 FQCN 桩注解（SofaService/SofaServiceBinding/TRpcService）+ 两扫描器单测

## 2. starter 与测试

- [x] 2.1 双 starter 嵌套配置 ×2（@ConditionalOnClass(name="...") 字符串守卫）
- [x] 2.2 双端集成测试（桩注解 + 服务 Bean → /apis 断言 SOFA_TR 与 TRPC 条目）
- [x] 2.3 全量构建全绿

## 3. 收尾

- [x] 3.1 README TR 双协议条目（含 tRPC FQCN 假设的边界说明）+ 首行简介更新
- [x] 3.2 功能清单勾选 Slice 10/11 + spec 同步 + 归档 + 提交
