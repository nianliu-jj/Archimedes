# Spec Delta: rpc-scanning

## ADDED Requirements

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
