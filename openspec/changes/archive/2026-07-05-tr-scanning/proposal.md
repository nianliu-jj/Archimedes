# Proposal: tr-scanning

## Why

TR 双协议（SOFARPC TR 与腾讯 tRPC）是既定四 RPC 协议的收官两站（用户决策：两者都做）。二者的 Spring 侧 provider 声明完全同构——注解标注的服务 Bean（`@SofaService` / `@TRpcService`），扫描实现可共模式一次落地，故合并为一个 change（对应功能清单 Slice 10 与 11）。

## What Changes

- `RpcApiInfo` 增加服务级 `metadata` 字段（Map，可 null；承载 SOFA 的 uniqueId/bindings 等协议特有信息）
- core 新增两个**零第三方依赖的反射式扫描器**：
  - `SofaTrRpcScanner`：扫描 `com.alipay.sofa.runtime.api.annotation.SofaService` 注解 Bean，取 `interfaceType`（未指定时回退 Bean 的唯一实现接口）、`uniqueId`、`bindings`（bindingType 列表）；protocol=`SOFA_TR`
  - `TrpcRpcScanner`：扫描 `com.tencent.trpc.spring.annotation.TRpcService` 注解 Bean，防御式读取常见属性；protocol=`TRPC`
  - 注解经 `ClassUtils.forName` + Spring `AnnotatedElementUtils` 读取属性——**不引入 sofa/trpc 任何编译依赖**，规避 sofa-boot 3.x(javax)/4.x(jakarta) 双轨与 tRPC 坐标可得性问题
- 双 starter 嵌套条件配置用 `@ConditionalOnClass(name="...")` 字符串形式守卫（同样零编译依赖）
- 测试策略：test 源码定义与目标框架同 FQCN 的最小注解（仅测试用，不随 jar 发布），驱动真实反射路径；core 单测 + 双端集成测试
- UI 无改动（RPC 分区 protocol 徽标自动出现 SOFA_TR/TRPC）

## Capabilities

### New Capabilities

（无——TR 双协议属 `rpc-scanning` 能力扩展）

### Modified Capabilities

- `rpc-scanning`: 新增 SOFARPC TR 与 tRPC 两个扫描 Requirement。

## Impact

- **core**：两个扫描器 + RpcApiInfo.metadata；零新依赖
- **starter×2**：各两个嵌套配置
- **边界如实声明**：tRPC 注解 FQCN 按开源 tRPC-Java 的 Spring 集成公开形态假设；FQCN 不匹配的宿主中条件不命中、扫描器不装配（安全退化），后续可按真实反馈校正
