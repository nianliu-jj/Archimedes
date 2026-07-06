# Tasks: cross-thread-mdc

## 1. core propagation 包

- [x] 1.1 `MdcWrappers`（Runnable/Callable/Supplier/Executor/ExecutorService/ScheduledExecutorService 六个 wrap；快照-恢复-还原语义）
- [x] 1.2 `MdcTaskDecorator` + 委托包装器 `MdcExecutor`/`MdcExecutorService`/`MdcScheduledExecutorService`（覆盖全部提交方法）
- [x] 1.3 `MdcExecutorBeanPostProcessor`（三形态分路：TPTE 注 decorator 组合已有、接口 Bean 同接口包装、TaskScheduler 跳过；exclude-beans；幂等防御）
- [x] 1.4 `TraceProperties` 嵌套 `propagation`（enabled/exclude-beans）
- [x] 1.5 core 单测：wrap 三件套语义（传递+还原+空快照）、ExecutorService 全方法、decorator 组合、BPP 三形态分路与排除

## 2. starter 装配

- [x] 2.1 双 starter trace 自动装配注册 static BPP（Binder 读配置）+ propagation.enabled=false 零注册测试

## 3. 集成测试与样例

- [x] 3.1 sb3 集成测试三路：@Async 归一、自定义 ExecutorService Bean 归一、commonPool+MdcWrappers 归一
- [x] 3.2 sb2 镜像（Java 8）
- [x] 3.3 example×2：@Async 演示端点（主/异步线程 traceId 对照）
- [x] 3.4 全量构建全绿 + 真机验证

## 4. 收尾

- [x] 4.1 README 跨线程章节（自动覆盖范围 / 盲区与 MdcWrappers / exclude-beans）
- [x] 4.2 功能清单勾选 Slice 5 + 决策表 TTL 措辞修正（纯 MDC 快照实现，零新依赖）
