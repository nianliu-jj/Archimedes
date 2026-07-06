# Spec Delta: mdc-propagation

## ADDED Requirements

### Requirement: Spring 线程池自动传递 trace 上下文
启用传递时（默认启用），提交到 Spring 容器管理的 Executor 形态 Bean（`ThreadPoolTaskExecutor`、`ExecutorService`、`ScheduledExecutorService`、`Executor`，含 `@Async` 默认线程池）的任务 SHALL 自动携带提交时刻的 MDC 上下文；任务执行完毕后 SHALL 还原该线程原有 MDC。`TaskScheduler` 形态的 Bean SHALL 不被包装。

#### Scenario: @Async 方法日志归入同一 traceId
- **WHEN** 请求线程 MDC 有 traceId 且业务调用 `@Async` 方法
- **THEN** 异步方法内 `MDC.get("traceId")` 与请求线程一致

#### Scenario: 自定义 ExecutorService Bean 自动接入
- **WHEN** 宿主定义了 `ExecutorService` 类型的 Bean 并在请求中向其提交任务
- **THEN** 任务内 MDC traceId 与请求线程一致，任务结束后池内线程的 MDC 还原

### Requirement: 手动包装工具覆盖自动化盲区
系统 SHALL 提供 `MdcWrappers` 工具：`wrap(Runnable)`、`wrap(Callable)`、`wrap(Supplier)`、`wrap(Executor)`、`wrap(ExecutorService)`、`wrap(ScheduledExecutorService)`，使 `CompletableFuture` 默认线程池、宿主自建裸线程池等场景可通过一行包装获得传递。

#### Scenario: commonPool 上的 CompletableFuture
- **WHEN** 业务代码使用 `CompletableFuture.supplyAsync(MdcWrappers.wrap(supplier))`
- **THEN** supplier 内 MDC traceId 与提交线程一致

### Requirement: 传递可配置与可排除
系统 SHALL 提供 `archimedes.trace.propagation.enabled`（默认 true）与 `archimedes.trace.propagation.exclude-beans`（Bean 名列表）；enabled=false 时 SHALL 不注册任何包装处理器，被排除的 Bean SHALL 保持原样。

#### Scenario: 关闭自动传递
- **WHEN** 配置 `archimedes.trace.propagation.enabled=false`
- **THEN** 容器中无 Executor 包装处理器，Executor Bean 原样保留

#### Scenario: 按名排除
- **WHEN** `exclude-beans` 包含某 `ExecutorService` Bean 名
- **THEN** 该 Bean 不被包装，其任务不自动携带 MDC
