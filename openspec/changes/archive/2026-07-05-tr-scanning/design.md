# Design: tr-scanning

## Context

SOFA 与 tRPC 的 provider 在 Spring 里都是"注解标注的服务实现 Bean"：`@SofaService(interfaceType=Xxx.class, uniqueId=..., bindings=@SofaServiceBinding(bindingType="tr"))`、`@TRpcService(...)`。与 Dubbo（引编译依赖，API 面稳定且广泛使用）不同，这两家引编译依赖代价高：sofa-boot 3.x/4.x 按 javax/jakarta 分轨（要为双 starter 选两套版本），tRPC-Java 的 maven 坐标在中央仓库的可得性有不确定性（本会话已遇镜像抖动）。而我们只需要**读注解属性**——反射完全够用。

## Goals / Non-Goals

**Goals:**
- 两协议 provider 契约进入 `rpcApis`；宿主无对应框架时零装配零影响
- 零新增编译依赖；SB2/SB3 单份实现

**Non-Goals:**
- SOFA XML 方式（`<sofa:service>`）声明的服务（注解方式为主流；XML 场景后续按需）
- tRPC 的 .proto 契约展开（方法签名以 Java 接口反射为准）
- TR 协议报文层细节（展示的是服务声明契约）

## Decisions

### D1：反射式注解扫描，`@ConditionalOnClass(name="...")` 字符串守卫

嵌套配置用注解的 name 属性（字符串）做条件——Spring 经 ASM 求值，无需编译期类型。扫描器构造时 `ClassUtils.forName(annotationFqcn)`（条件已保证在场），`getBeansWithAnnotation` + `AnnotatedElementUtils.getMergedAnnotationAttributes` 读属性。防御式读取：属性不存在/类型不符时跳过该属性而非失败。

### D2：serviceName 解析链（两协议共用）

注解的接口属性（SOFA `interfaceType` / tRPC 若有）→ 非 void/Object 即用；否则 Bean 用户类的**唯一**实现接口；多接口或无接口时用实现类自身 FQCN 并在 metadata 标注 `serviceNameSource=implementationClass`。方法签名一律对解析出的类型反射（排除 Object 方法），与 Dubbo 路径同构。

### D3：协议特有信息进 metadata

`RpcApiInfo` 增加服务级 `metadata`（与方法级对称）：SOFA → `{uniqueId, bindings}`（bindings 取每个 `@SofaServiceBinding.bindingType` 逗号连接，缺省绑定记 `bolt` 由框架决定——只记录声明值，不臆造默认）；tRPC → 防御式收集 `name/version/group` 等常见属性中实际存在者。version/group 字段：SOFA 无对应语义留 null（uniqueId 不冒充 version）；tRPC 若注解有同名属性则填充。

### D4：测试用同 FQCN 桩注解

test 源码定义 `com.alipay.sofa.runtime.api.annotation.SofaService`/`SofaServiceBinding` 与 `com.tencent.trpc.spring.annotation.TRpcService` 的最小版本（仅声明我们读取的属性）。这驱动的是**真实反射路径**（forName/合并注解属性），且不随 jar 发布、无坐标依赖。风险：桩与真实注解属性签名漂移——防御式读取（D1）保证漂移时降级而非崩溃。

## Risks / Trade-offs

- [tRPC 注解 FQCN 假设不匹配真实版本] → 条件不命中即静默不装配（零风险面）；proposal 如实声明，待真实宿主反馈校正
- [SOFA @SofaService 在 XML 场景缺位] → Non-Goal 明示
- [反射读属性的类型漂移] → AnnotationAttributes 防御式访问 + try/catch 每 Bean 隔离（单个 Bean 解析失败不影响其余）

## Migration Plan

1. core：RpcApiInfo.metadata + 两扫描器 + 桩注解 + 单测
2. starter×2：嵌套配置（字符串条件）+ 集成测试
3. 全量构建 + 收尾（README/清单/spec/归档/提交）

## Open Questions

（无）
