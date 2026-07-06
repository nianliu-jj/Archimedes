# Design: dubbo-scanning

## Context

Dubbo 服务在 Spring 容器中的注册形态：无论 `@DubboService` 注解还是 XML `<dubbo:service>`，最终都会产生 `org.apache.dubbo.config.spring.ServiceBean` Bean。需求文档提出的三种获取方式（ServiceConfig API / MetadataService / SPI 事件监听）中，MetadataService 依赖应用级元数据中心配置，SPI 监听引入时序耦合；直接扫 `ServiceBean` Bean 最贴合"运行时容器内自省"的项目基调（与 REST/WS 扫描同构）。

## Goals / Non-Goals

**Goals:**
- Dubbo provider 服务契约（接口、方法签名、version/group）进入 `/apis` 与 UI
- 通用 RPC 模型一次成型，gRPC/TR 后续零结构改动接入
- 宿主无 Dubbo 时零影响

**Non-Goals:**
- consumer 侧引用（`ReferenceBean`）展示——契约展示以 provider 为准
- Dubbo 泛化调用/在线调试
- 方法级 Dubbo 配置（timeout/retries 等运维参数）

## Decisions

### D1：数据模型分层——protocol 字段区分协议而非四套模型

`RpcApiInfo{protocol, serviceName, version, group, methods}`。四个 RPC 协议的契约本质同构（服务名+方法签名+版本分组），protocol 字段（DUBBO/GRPC/SOFA_TR/TRPC）做区分，UI 一个分区吃下全部——避免 `/apis` 出现 dubboApis/grpcApis/... 四个字段的碎片化。

### D2：扫描 `ServiceBean` 而非 ServiceConfig API / MetadataService

`getBeansOfType(ServiceBean.class)` 天然覆盖注解与 XML 两种注册方式；`getInterface()`/`getInterfaceClass()`/`getVersion()`/`getGroup()` 在 Dubbo 2.7 与 3.x 签名一致（core 单份编译成立）。方法签名经 `getInterfaceClass().getMethods()` 反射提取（排除 Object 方法）。

### D3：core 编译依赖 dubbo 3.2.x（optional），运行兼容 2.7+

所用 API 面在 2.7/3.x 一致；`dubbo.version` 由父 POM 属性管理（3.2.16）。starter 嵌套配置以 `@ConditionalOnClass(ServiceBean.class)` 守卫——ASM 读注解不加载嵌套类，宿主无 Dubbo 时零加载零影响（与 WS 扫描同模式）。

### D4：集成测试的 Dubbo 环境收敛

内嵌 provider 最小化：`dubbo.registry.address=N/A`（本地导出不注册）、`dubbo.application.qos-enable=false`（避免 22222 端口冲突）、`dubbo.protocol.port=-1`（随机端口）。测试断言只看扫描输出，不做 RPC 调用。

### D5：UI 单个 RPC 分区

列：Protocol 徽标 | Service | Version/Group | Methods（`name(paramSimpleNames) : returnSimpleName` 每行一个）。参数/返回类型展示 simple name（悬浮 title 放全限定名），避免表格爆宽。

## Risks / Trade-offs

- [Dubbo 3 应用级注册模型下 ServiceBean 仍存在吗] → 存在：ServiceBean 是 Spring 侧配置持有者，与注册模型（接口级/应用级）无关
- [test JVM 中 Dubbo 静态状态跨上下文残留（ApplicationModel 单例）] → 每模块仅一个 Dubbo 测试类；qos/registry 均本地化
- [dubbo jar 体积大拖慢构建] → optional + test scope 限定，仅首次下载

## Migration Plan

1. core：模型 + SPI + ApiCatalog 扩展 + DubboRpcScanner + 控制器聚合 + 单测
2. starter×2：嵌套配置 + 集成测试；UI RPC 分区
3. 全量构建 + 既有测试回归（rpcApis 空数组断言补充）

## Open Questions

（无）
