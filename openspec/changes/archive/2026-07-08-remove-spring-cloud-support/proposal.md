## Why

用户决策：Archimedes 作为依赖**不对 Spring Cloud 提供支持**。当前配置热更新在 classpath 存在 Spring Cloud 时会反射发布 `EnvironmentChangeEvent`（联动 `@RefreshScope` 生态），该行为超出既定支持边界，需要移除，避免对 Spring Cloud 生态形成隐性承诺与维护负担。

## What Changes

- **BREAKING**（行为收窄）：`DynamicConfigManager` 移除 Spring Cloud `EnvironmentChangeEvent` 的反射构造与发布逻辑——配置热更新后只发布 `ArchimedesConfigChangedEvent`，与 classpath 是否存在 Spring Cloud 无关。
- 删除 core 测试源中的同 FQCN 测试桩 `org.springframework.cloud.context.environment.EnvironmentChangeEvent` 及对应测试断言。
- 文档同步：README 事件联动说明、`docs/功能清单与任务列表.md`（事件条目、Slice 14.3 Spring Cloud 兼容性验证任务移除、锁定决策表补记"不支持 Spring Cloud"）。
- 不改动：历史需求文档（docs/思考.md、docs/需求.md）、已归档 change、`ArchimedesLoggingEnvironmentPostProcessor` 的幂等防御（多次调用防御是通用健壮性，非 SC 支持）。

## Capabilities

### New Capabilities

<!-- 无 -->

### Modified Capabilities

- `config-management`: 「热更新联动刷新」需求移除 Spring Cloud `EnvironmentChangeEvent` 反射发布条款，事件发布收窄为仅 `ArchimedesConfigChangedEvent`。

## Impact

- `archimedes-core`：`env/DynamicConfigManager`（删反射发布）、`env/ArchimedesConfigChangedEvent`（javadoc）、删除测试桩、`DynamicConfigManagerTest` 断言收窄。
- 文档：README、docs/功能清单与任务列表.md。
- 宿主影响：曾依赖该隐式联动的 Spring Cloud 宿主需自行监听 `ArchimedesConfigChangedEvent` 转发（README 不再宣传该场景）。
