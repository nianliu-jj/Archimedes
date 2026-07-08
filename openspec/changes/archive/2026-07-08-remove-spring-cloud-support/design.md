## Context

Slice 16 的配置热更新为兼顾 Spring Cloud 生态做了"零编译依赖 + 反射可选发布 `EnvironmentChangeEvent`"的联动设计（同 FQCN 测试桩驱动验证）。用户现已明确支持边界：**不对 Spring Cloud 提供支持**，该联动需整体移除。

## Goals / Non-Goals

**Goals:**

- 配置热更新的事件发布收窄为仅 `ArchimedesConfigChangedEvent`，删除全部 Spring Cloud 反射发布代码与测试桩。
- 现行文档（README / 功能清单）与主 spec 同步收窄，并在锁定决策表记录该边界。

**Non-Goals:**

- 不改历史需求文档与已归档 change（保持历史真实）。
- 不移除与 Spring Cloud 无关的通用防御逻辑（如 EnvironmentPostProcessor 幂等——多次调用防御是通用健壮性）。
- 不新增任何替代联动机制（宿主如需可自行监听 `ArchimedesConfigChangedEvent`）。

## Decisions

1. **只删不改架构**：`DynamicConfigManager.publishEvents` 退化为单事件发布，方法与调用点保留（重绑定→发布顺序不变），删除 `CLOUD_EVENT_CLASS` 常量与反射块及相关 import。
2. **测试桩整体删除**：`org.springframework.cloud.context.environment.EnvironmentChangeEvent` 桩类与 `DynamicConfigManagerTest` 中的对应断言一并移除，事件测试只验证自有事件。
3. **决策落档**：`docs/功能清单与任务列表.md` 锁定决策表新增"Spring Cloud：不提供支持"行；工程与分发中的"Spring Cloud 项目兼容性验证"待办与 Slice 14.3 移除。

## Risks / Trade-offs

- [曾依赖隐式 EnvironmentChangeEvent 联动的 SC 宿主行为变化] → 属既定边界收窄（BREAKING 标注）；宿主可监听 `ArchimedesConfigChangedEvent` 自行转发，README 不再宣传该场景。

## Migration Plan

纯删除，无配置项变化；`ArchimedesConfigChangedEvent` 行为不变，非 Spring Cloud 宿主零感知。
