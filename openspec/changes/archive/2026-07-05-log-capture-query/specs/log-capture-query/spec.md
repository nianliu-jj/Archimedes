# Spec Delta: log-capture-query

## ADDED Requirements

### Requirement: 结构化日志采集与格式解耦
启用采集时（默认启用，且宿主日志实现为 logback），系统 SHALL 以编程式挂载到 root logger 的 Appender 捕获日志事件的结构化字段（时间、级别、线程、logger、消息、spanId），MDC 中无 traceId 的事件 SHALL NOT 被采集；采集 SHALL 不依赖也不受宿主日志输出格式（pattern）影响。

#### Scenario: 业务日志被按 traceId 归集
- **WHEN** 一个携带 traceId 的请求执行期间业务代码打印日志
- **THEN** 该日志可通过该 traceId 查询到，且字段（线程、级别、消息）结构化完整

#### Scenario: 自定义 pattern 不影响采集
- **WHEN** 宿主自定义了 logback pattern
- **THEN** 按 traceId 查询的结果不受影响

#### Scenario: 非 logback 环境优雅跳过
- **WHEN** 宿主使用非 logback 日志实现
- **THEN** 采集组件不挂载且不影响应用启动，其余 Archimedes 功能正常

### Requirement: 多线程日志归入同一链路
同一请求经跨线程传递机制在其它线程产生的日志 SHALL 与请求线程日志归入同一 traceId 并可一次查询取回，条目 SHALL 保留各自线程名以供区分。

#### Scenario: 异步线程日志可查
- **WHEN** 请求触发 @Async 方法且两个线程各打一条日志
- **THEN** 按该 traceId 查询返回两条日志，线程名不同

### Requirement: LogStore SPI 与内存默认实现
日志存储 SHALL 以 `LogStore` SPI（`append` / `queryByTraceId` 分页）抽象；默认提供内存实现：全局条数上限（默认 10000）与单 trace 条数上限（默认 500）可配置，超限按最老 trace 整体淘汰；宿主注册自定义 `LogStore` Bean（如 Elasticsearch 实现）时默认实现 SHALL 让位。

#### Scenario: 自定义 LogStore 让位
- **WHEN** 宿主定义了自己的 LogStore Bean
- **THEN** 内存默认实现不注册，采集与查询走宿主实现

#### Scenario: 有界淘汰
- **WHEN** 采集条数超过全局上限
- **THEN** 最老的整条链路被淘汰，最近链路仍完整可查

### Requirement: 按 traceId 查询端点
系统 SHALL 提供 `GET {base-path}/logs/trace/{traceId}`（page/size 分页，按时间升序）返回 `{traceId,total,page,size,logs}`，以及 `GET {base-path}/trace/current` 返回当前请求 traceId；`archimedes.log.capture.enabled=false` 时上述 Bean SHALL 不注册。

#### Scenario: 分页查询
- **WHEN** 某 traceId 有 5 条日志且请求 page=1&size=3
- **THEN** 返回 total=5 与前 3 条（时间升序）

#### Scenario: 关闭采集
- **WHEN** 配置 `archimedes.log.capture.enabled=false`
- **THEN** 无采集与查询相关 Bean，查询路径返回 404

### Requirement: UI 链路日志查询分区
内置 UI SHALL 提供 Trace Logs 分区：输入 traceId 查询并按时间线渲染日志表格，不同线程的条目 SHALL 可视区分。

#### Scenario: 页面查询链路日志
- **WHEN** 在 UI 输入有效 traceId 并查询
- **THEN** 页面按时间顺序列出该链路全部已采集日志并标注线程
