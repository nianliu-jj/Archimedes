# Design: cross-thread-mdc

## Context

MDC 基于 ThreadLocal，任务提交到线程池后上下文丢失。需求要求"最细粒、最全面"，已锁定边界为"引入即用覆盖 Spring 世界、无 javaagent"。Spring 世界的线程形态：`@Async`（默认 applicationTaskExecutor）、用户定义的 `ThreadPoolTaskExecutor`/`ExecutorService`/`Executor` Bean、STOMP 内部通道执行器；自动化盲区：`CompletableFuture` 默认 `ForkJoinPool.commonPool`、用户 `new` 的裸线程池、`parallelStream()`。

## Goals / Non-Goals

**Goals:**
- Spring 容器管理的一切 Executor 形态 Bean：引入即用自动传递，宿主零改动
- 盲区提供一行式手动包装工具（`MdcWrappers`）
- 传递语义无泄漏：子线程执行完还原其原有 MDC（池化线程复用安全）

**Non-Goals:**
- javaagent / 字节码插桩（已锁定不做）
- `parallelStream()`、未包装的 commonPool 任务的自动覆盖（物理上必须 agent，文档明示边界）
- InheritableThreadLocal 方案（池化线程只继承一次，语义错误，业界共识弃用）

## Decisions

### D1：纯 MDC 快照传递，不引入 TTL 依赖（对既定决策的实现级修正）

原决策文本含"TTL 桥接 MDC"。实现推演结论：TTL 不加 agent 时，其能力=在任务提交点包装（`TtlRunnable`/`TtlExecutors`）——与直接快照 MDC 的包装完全同构，但要求上下文存储在 `TransmittableThreadLocal` 中，而 MDC 的存储在 slf4j 实现内部，桥接需要替换 `MDCAdapter`（logback 私有绑定 hack，脆弱）。**同等覆盖下选零依赖、零 hack 的直接快照**。若未来走 agent 路线，`MdcWrappers` 是唯一底层入口，可整体换底。

传递语义（所有包装器统一）：
```
提交时：snapshot = MDC.getCopyOfContextMap()
子线程执行前：backup = 当前 MDC；setContextMap(snapshot)（null → clear）
子线程执行后（finally）：还原 backup（null → clear）
```

### D2：按 Bean 形态分三种接入方式（保类型优先）

| Bean 形态 | 接入方式 | 类型影响 |
|---|---|---|
| `ThreadPoolTaskExecutor`（含子类） | `postProcessBeforeInitialization` 注入 `MdcTaskDecorator`；已有 decorator 则组合（ours ∘ theirs） | **Bean 类型不变**（最安全，@Async 默认池即此形态） |
| `ScheduledExecutorService` / `ExecutorService` / `Executor` 接口 Bean | `postProcessAfterInitialization` 返回同接口委托包装器 | Bean 实现类改变；注入具体类的宿主代码需 `exclude-beans` 逃生 |
| `TaskScheduler` 形态（ThreadPoolTaskScheduler 等） | **跳过** | 定时任务不源于请求上下文，包装无意义且易伤 Spring 内部机制 |

已有 decorator 的读取：`ThreadPoolTaskExecutor` 无 getter，反射读私有字段 `taskDecorator`（Spring 5.3/6 字段名一致，失败则仅用我们的 decorator 并 debug 日志）。

### D3：BPP 的注册与配置读取

- static `@Bean` 注册于两个 starter 的 `ArchimedesTraceAutoConfiguration`（gate：外层 `archimedes.trace.enabled` + 自身 `archimedes.trace.propagation.enabled`，均 matchIfMissing=true）
- BPP 构造注入 `Environment`，用 `Binder` 读取 `propagation.exclude-beans`——不注入 `TraceProperties` Bean，避免 BPP 早期实例化把 `@ConfigurationProperties` 绑定链提前拉起（binding 本身也是 BPP）
- 自我防御：已是我们包装器类型的 Bean 直接跳过（幂等）

### D4：`MdcWrappers` 是唯一公开手动 API

`wrap(Runnable/Callable/Supplier)` + `wrap(Executor/ExecutorService/ScheduledExecutorService)`。`CompletableFuture.supplyAsync(MdcWrappers.wrap(supplier))` 即覆盖 commonPool 场景。委托包装器类不公开构造，统一从 `MdcWrappers` 进入。

## Risks / Trade-offs

- [接口型 Bean 被换实现类，宿主按具体类注入会失败] → `exclude-beans` 排除 + README 明示；`ThreadPoolTaskExecutor`（最常见形态）走注入 decorator 路线不受影响
- [反射读 `taskDecorator` 私有字段在未来 Spring 版本变名] → try/catch 降级为仅设我们的 decorator，功能不损（用户 decorator 丢失时 debug 日志提示）
- [`invokeAll`/`invokeAny` 的集合包装遗漏] → 委托包装器覆盖全部提交方法，单测逐方法断言
- [定时任务线程（@Scheduled）无 traceId] → 设计边界：不源于请求；Slice 6 文档说明

## Migration Plan

1. core：包装器 + 装饰器 + BPP + 属性 + 单测
2. starter×2：注册 static BPP Bean + 装配测试
3. 双端集成测试（三路）+ example 演示 + 全量构建 + 真机验证

## Open Questions

（无）
