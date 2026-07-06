# Proposal: cross-thread-mdc

## Why

需求硬性要求：接口链路中开启多线程时，多线程产生的日志必须归入同一 traceId（`docs/项目需求.md` §3.2.3）。Slice 4 建立的 MDC 上下文只存在于请求线程；本 slice 让它随任务提交自动传递到 Spring 世界的一切线程池，为 Slice 6 的按 traceId 日志采集补全最后一块前置。

## What Changes

- core 新增 `trace/propagation` 包：
  - `MdcWrappers`：`wrap(Runnable/Callable/Supplier/Executor/ExecutorService/ScheduledExecutorService)` 手动包装工具（覆盖 `CompletableFuture`+`commonPool`、裸线程池等自动化盲区）
  - `MdcTaskDecorator implements TaskDecorator`
  - `MdcExecutor` / `MdcExecutorService` / `MdcScheduledExecutorService` 委托包装器
  - `MdcExecutorBeanPostProcessor`：对容器内所有 Executor 形态的 Bean 自动接入传递——`ThreadPoolTaskExecutor` 注入 TaskDecorator（保持 Bean 类型不变，与用户已设置的 decorator 组合），`ExecutorService`/`ScheduledExecutorService`/`Executor` 接口 Bean 以同接口包装器替换；`TaskScheduler` 类型跳过
- `TraceProperties` 新增嵌套配置 `archimedes.trace.propagation.*`：`enabled`（默认 true）、`exclude-beans`（按 Bean 名排除）
- 两个 starter 的 `ArchimedesTraceAutoConfiguration` 注册该 BPP（static Bean，经 `Binder` 读配置避免早期初始化连锁）
- 传递语义：提交时快照 MDC → 子线程执行前恢复快照 → 执行后还原子线程原有 MDC
- **实现取代说明**：原决策中的"TTL 桥接"在无 javaagent 前提下对 MDC 传递没有增量收益（TTL 的增量在 agent 插桩 commonPool/裸池），本 slice 以纯 MDC 快照传递实现同等"Spring 世界"覆盖，零新增第三方依赖；若未来引入 agent 路线可在 `MdcWrappers` 之下替换基底。
- example×2 新增 `@Async` 多线程演示端点

## Capabilities

### New Capabilities

- `mdc-propagation`: MDC trace 上下文的跨线程传递——Spring 线程池自动接入、手动包装工具、配置面与排除机制。

### Modified Capabilities

（无）

## Impact

- **core**：新增 propagation 包；`TraceProperties` 增加嵌套项；无新第三方依赖
- **starter×2**：trace 自动装配各加一个 static BPP Bean
- **测试**：core 单测（包装器语义、还原语义、装饰器组合）；双端集成测试（@Async / 自定义 ExecutorService Bean / commonPool+手动 wrap 三路）
- **风险面**：BPP 替换接口型 Executor Bean 的类型可见性（注入具体实现类的宿主代码）——提供 `exclude-beans` 逃生口并文档说明
