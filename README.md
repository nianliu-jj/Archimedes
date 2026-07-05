# Archimedes

引入即用的 Spring Boot API 可观测性依赖：自动扫描宿主应用的接口契约（当前支持 REST 与 WebSocket，后续将支持 Dubbo / gRPC / SOFARPC-TR / tRPC），通过内置端点与 UI 页面展示。

## 已支持的接口契约

- **REST**：`@RestController` 的路径、HTTP 方法、参数（含来源与 required）、返回类型、consumes/produces、`@Deprecated` 标记
- **WebSocket**（三种形态，宿主未使用 WebSocket 时零影响）：
  - `@ServerEndpoint` 注解端点（SB2=javax / SB3=jakarta；须注册为 Spring Bean，容器 SCI 直接注册的端点不在覆盖内）
  - `WebSocketConfigurer` 注册的 handler（含 SockJS 标记）
  - STOMP：握手端点、`@MessageMapping`、`@SubscribeMapping` 目的地
- **RPC — Dubbo**（宿主未使用 Dubbo 时零影响）：扫描 provider `ServiceBean`（覆盖 `@DubboService` 注解与 XML 注册），提取接口全限定名、version、group 与方法签名（入参/返回类型），Dubbo 2.7 与 3.x 兼容
- **RPC — gRPC**（宿主未使用 gRPC 时零影响）：扫描 `BindableService` Bean（`@GrpcService` 等主流集成通吃），提取服务名、方法、streaming 形态与消息类型；无需注册 Server Reflection 或启动 gRPC Server

`GET {base-path}/apis` 返回分组结构：`{"restApis": [...], "webSocketApis": [...], "rpcApis": [...]}`（协议不存在时为空数组；gRPC / SOFARPC-TR / tRPC 后续同样并入 `rpcApis`，以 `protocol` 字段区分）。

## 模块结构

| 模块 | 说明 |
|---|---|
| `archimedes-core` | 框架无关核心：数据模型、扫描器、端点控制器、UI 资源（按 Spring Boot 2.7 基线编译，Java 8 字节码，单 jar 双端复用；禁止 servlet API 依赖） |
| `archimedes-spring-boot-2-starter` | Spring Boot 2.7.x（javax）薄注册层，`spring.factories` 注册 |
| `archimedes-spring-boot-3-starter` | Spring Boot 3.x（jakarta）薄注册层，`AutoConfiguration.imports` 注册 |
| `example` | SB3 示例应用，验证"引入即用" |
| `example-boot2` | SB 2.7 示例应用（javax），验证 2.x starter 的"引入即用" |

## 快速开始

按宿主应用的 Spring Boot 大版本二选一：

```xml
<!-- Spring Boot 3.x -->
<dependency>
    <groupId>io.github.nianliu</groupId>
    <artifactId>archimedes-spring-boot-3-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot 2.7.x -->
<dependency>
    <groupId>io.github.nianliu</groupId>
    <artifactId>archimedes-spring-boot-2-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

启动后访问：

- `GET /archimedes/apis` — 接口契约 JSON
- `GET /archimedes` — 内置 UI 页面

## 版本矩阵

| Starter | Spring Boot | Servlet API | 最低 Java |
|---|---|---|---|
| `archimedes-spring-boot-2-starter` | 2.7.x | javax | 8 |
| `archimedes-spring-boot-3-starter` | 3.x | jakarta | 17 |

## 配置

```yaml
archimedes:
  api:
    enabled: true          # API 展示总开关，false 时不注册任何相关 Bean
    base-path: /archimedes # 端点根路径
    ui-enabled: true       # 是否挂载内置 UI
    base-packages: []      # 非空时只扫描这些包前缀下的 Controller
  trace:
    enabled: true              # 链路追踪总开关，false 时不注册 Filter 与相关 Bean
    header-name: X-Trace-Id    # 透传/回写 traceId 的请求头
    response-header: true      # 是否在响应头回写 traceId
    mdc-key: traceId           # traceId 在 MDC 中的 key
    span-id-key: spanId        # spanId 在 MDC 中的 key
    use-project-trace-id: false # true=信任宿主自有 Filter 已写入的 MDC traceId（不覆盖不清理）
```

## 链路追踪（traceId）

引入 starter 后每个 HTTP 请求自动建立 trace 上下文：

- **解析优先级**：用户 `TraceIdResolver` Bean → 请求头（`header-name`）→ 宿主 MDC（需 `use-project-trace-id=true`）→ `TraceIdGenerator` 生成
- **自定义生成算法**：注册自己的 `TraceIdGenerator` Bean（如雪花算法）即可替换默认 UUID（`@ConditionalOnMissingBean` 让位）
- **精准清理**：请求结束只回滚本请求写入的 MDC 键并恢复旧值，绝不 `MDC.clear()`，宿主自有 MDC 上下文零破坏

## 跨线程 traceId 传递

Spring 容器管理的线程池**引入即用自动覆盖**（可用 `archimedes.trace.propagation.enabled=false` 关闭）：

- `ThreadPoolTaskExecutor` Bean（含 `@Async` 线程池）：自动注入 `MdcTaskDecorator`，Bean 类型不变，已有 decorator 自动组合
- `ExecutorService` / `ScheduledExecutorService` / `Executor` 接口 Bean：自动替换为同接口的 MDC 传递包装器（若宿主按具体实现类注入，可用 `archimedes.trace.propagation.exclude-beans` 排除）
- `TaskScheduler` 形态（`@Scheduled` 定时任务）不包装——定时任务不源于请求上下文

**自动化盲区**（物理上需 javaagent 才能自动覆盖，本项目提供一行式手动包装）：

```java
// CompletableFuture 默认 commonPool、自建裸线程池等场景
CompletableFuture.supplyAsync(MdcWrappers.wrap(() -> doWork()));
executor.submit(MdcWrappers.wrap(runnable));
```

注意：若宿主定义了任意 `Executor` Bean（例如启用 STOMP 后其内部通道执行器），Spring Boot 的 `applicationTaskExecutor` 会退避，`@Async` 退化为非容器管理的 `SimpleAsyncTaskExecutor`（无法自动传递）。Archimedes 启动时会检测该场景并打 WARN——按提示定义名为 `taskExecutor` 的 Bean 即可回到覆盖范围。

## 链路日志查询

logback 环境下**引入即用**：结构化采集 Appender 编程式挂载 root logger（与日志输出格式完全解耦，改 pattern 不影响查询），只采集 MDC 含 traceId 的日志。

- `GET {base-path}/logs/trace/{traceId}?page=1&size=200` — 按 traceId 查询该链路全部日志（含跨线程），时间升序分页
- `GET {base-path}/trace/current` — 当前请求的 traceId
- 内置 UI 的 **Trace Logs** 分区：输入 traceId 查询，时间线展示、按线程着色区分

存储默认为内存有界实现（重启即失）：全局上限 `archimedes.log.capture.max-entries`（默认 10000）、单链路上限 `max-entries-per-trace`（默认 500），超限按最老链路整体淘汰。生产持久化（如 Elasticsearch）通过注册自定义 `LogStore` Bean 接入，内存实现自动让位（`@ConditionalOnMissingBean`）。`archimedes.log.capture.enabled=false` 可整体关闭。

## 日志配置兜底

宿主 **没有任何 logback 配置**（无 `logback-spring.xml`/`logback.xml` 等且未设 `logging.config`）时，Archimedes 在日志系统初始化前注入内置配置：控制台 + 滚动文件双输出，pattern 含 `[traceId] [spanId]`。宿主有任何自有配置时**完全不介入**。

```yaml
archimedes:
  log:
    fallback-enabled: true   # 兜底总开关（false 时行为与未引入 Archimedes 一致）
    pattern: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] [%X{spanId:-}] %-5level %logger{36} - %msg%n"
    path: ./logs             # 滚动文件目录
    max-history: 30          # 保留天数
    max-file-size: 100MB     # 单文件大小
```

修改 pattern 不影响按 traceId 查询——采集是结构化的，与输出格式解耦。

## 构建与示例

```bash
mvn clean install                # 构建全部模块（JDK 21）
mvn -pl example -am spring-boot:run        # SB3 示例，http://localhost:8080/archimedes
mvn -pl example-boot2 -am spring-boot:run  # SB2.7 示例，http://localhost:8081/archimedes
```
