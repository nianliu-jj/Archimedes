# Proposal: dubbo-scanning

## Why

RPC 契约展示是需求四大接口形态之一（`docs/项目需求.md` §四），Dubbo 是既定四协议（Dubbo/gRPC/SOFARPC-TR/tRPC）中生态最广的第一站。本 slice 同时落地**通用 RPC 数据模型与 `rpcApis` 分组字段**，为后续三个协议提供直接复用的结构。

## What Changes

- core 新增通用 RPC 模型与 SPI（全协议复用）：
  - `model/RpcApiInfo`（protocol/serviceName/version/group/methods）与 `model/RpcMethodInfo`（methodName/parameterTypes/returnType）
  - `scanner/rpc/RpcApiContributor` SPI（与 WebSocketApiContributor 同构）
  - `ApiCatalog` 增加 `rpcApis` 字段（空数组约定不变；既有二参构造保留委托）
- core 新增 `scanner/rpc/DubboRpcScanner`：扫描容器中的 `ServiceBean`（覆盖 `@DubboService` 注解与 XML 两种注册方式），提取接口全限定名、version、group 与接口方法签名（入参类型/返回类型）；`org.apache.dubbo.config.spring.ServiceBean` 的 API 面（getInterface/getInterfaceClass/getVersion/getGroup）在 Dubbo 2.7 与 3.x 一致
- core pom 增加 `org.apache.dubbo:dubbo` optional 依赖（编译用，版本自管 `dubbo.version`）
- 双 starter 自动装配增加嵌套条件配置（`@ConditionalOnClass(ServiceBean.class)`）
- UI 新增 **RPC APIs** 分区（protocol 徽标 + 方法签名列表，四协议共用）
- 双端集成测试：内嵌 Dubbo provider（registry N/A 本地导出）验证扫描输出
- example 不加 Dubbo（避免演示应用依赖膨胀，集成测试已覆盖真实容器）

## Capabilities

### New Capabilities

- `rpc-scanning`: 通用 RPC 契约模型 + Dubbo 扫描（后续 gRPC/SOFARPC-TR/tRPC 以 ADDED Requirement 扩展本能力）。

### Modified Capabilities

- `api-grouping`: `/apis` 分组结构新增 `rpcApis` 字段（协议不存在时空数组），UI 相应新增 RPC 分区。

## Impact

- **core**：新增模型/SPI/扫描器 + dubbo optional 依赖；`ApiCatalog`/控制器聚合扩展
- **starter×2**：各一个嵌套条件配置；测试依赖 `dubbo-spring-boot-starter`（test scope）
- **UI**：第三个接口分区
- **测试**：Dubbo 集成测试需规避 QoS 端口冲突（qos-enable=false）与注册中心（N/A）
