# Archimedes

引入即用的 Spring Boot API 可观测性 Starter —— 自动扫描宿主应用的全形态接口契约，内置 UI 探索与在线调试，并提供全链路 traceId 追踪、跨线程 MDC 传递与按 traceId 的日志采集查询。

## 特性一览

- **全协议契约扫描** — REST / WebSocket / Dubbo / gRPC / SOFARPC-TR / tRPC，零配置即出
- **内置 API Explorer UI** — Tab 化协议浏览、文本过滤、REST 在线调试、trace 日志时间线
- **请求/响应体结构提取** — 自动解析字段树（字段名 / 类型 / 必填 / 说明 / 嵌套），解包 `ResponseEntity`/`Mono`/`Flux`/集合等包装
- **全链路 traceId** — 每请求自动建立 trace 上下文，支持自定义生成算法与解析策略
- **跨线程 MDC 传递** — Spring 容器管理的线程池自动覆盖，`@Async` / `ExecutorService` / `ScheduledExecutorService` 开箱即用
- **链路日志采集与查询** — Logback 环境下自动挂载结构化采集 Appender，按 traceId 查询完整链路日志
- **配置中心** — 全量配置可视化（来源标注 + 敏感值脱敏）与运行时热更新（动态属性源 + `@ConfigurationProperties` 重绑定 + 事件联动）
- **数据库监控** — DataSource 零侵入代理：SQL 明细（语句/参数/耗时/行数/异常/traceId 关联）、聚合统计与慢 SQL、HikariCP 连接池指标、多数据源
- **双端兼容** — 同时支持 Spring Boot 2.7.x（javax）与 3.x（jakarta），Servlet 与 WebFlux 双栈

## 模块结构

```
archimedes-parent
├── archimedes-core                      # 框架无关核心：模型、扫描器、端点、UI 资源
├── archimedes-spring-boot-2-starter     # Spring Boot 2.7.x 自动装配层
├── archimedes-spring-boot-3-starter     # Spring Boot 3.x 自动装配层
├── example                              # SB3 示例应用（端口 8080）
├── example-boot2                        # SB2.7 示例应用（端口 8081）
└── example-all                          # 全功能演示（端口 8082）：覆盖所有协议与特性
```

## 快速开始

### 1. 引入依赖

按宿主应用的 Spring Boot 大版本二选一：

```xml
<!-- Spring Boot 3.x -->
<dependency>
    <groupId>io.github.nianliu</groupId>
    <artifactId>archimedes-spring-boot-3-starter</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>

<!-- Spring Boot 2.7.x -->
<dependency>
    <groupId>io.github.nianliu</groupId>
    <artifactId>archimedes-spring-boot-2-starter</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
```

### 2. 启动访问

无需任何配置，启动应用后即可使用：

| 端点 | 说明 |
|---|---|
| `GET /archimedes` | 内置 API Explorer UI |
| `GET /archimedes/apis` | 接口契约 JSON（包含 REST / WebSocket / RPC 分组） |
| `GET /archimedes/logs/trace/{traceId}` | 按 traceId 查询链路日志 |
| `GET /archimedes/trace/current` | 当前请求的 traceId |
| `GET /archimedes/config` | 全量配置查询（按属性源分组、敏感值脱敏） |
| `POST /archimedes/config/update` | 配置热更新（body `{key, value}`；value 缺省=删除覆盖恢复原值） |
| `GET /archimedes/db` | 数据库监控（连接池指标 + SQL 统计 + 最近执行 + 慢 SQL） |

## 版本矩阵

| Starter | Spring Boot | Servlet API | 最低 Java |
|---|---|---|---|
| `archimedes-spring-boot-2-starter` | 2.7.x | javax | 8 |
| `archimedes-spring-boot-3-starter` | 3.x | jakarta | 17 |

**Web 栈支持**：Servlet（spring-webmvc）与 Reactive（spring-webflux）双栈均已支持。WebFlux 宿主下自动装配响应式 REST 扫描器，`/archimedes/apis` 与内置 UI 同等可用。

## 已支持的接口契约

### REST

扫描 `@RestController` 的路径、HTTP 方法、参数（含来源与 required）、返回类型、consumes/produces、`@Deprecated` 标记。

**请求/响应体结构**：`@RequestBody` 参数类型与返回类型自动解析为字段树。字段说明反射读取 Swagger v3（`@Schema`/`@Parameter`）、Swagger v2（`@ApiModelProperty`/`@ApiParam`）、Jackson（`@JsonPropertyDescription`），均为零编译依赖。UI 调试面板按结构自动生成示例 JSON 预填。

### WebSocket

三种形态，宿主未使用 WebSocket 时零影响：

- `@ServerEndpoint` 注解端点（SB2 = javax / SB3 = jakarta）
- `WebSocketConfigurer` 注册的 handler（含 SockJS 标记）
- STOMP：握手端点、`@MessageMapping`、`@SubscribeMapping` 目的地

### RPC

四类 RPC 协议共用 `rpcApis` 字段，以 `protocol` 字段区分，宿主未使用对应协议时零影响：

| 协议 | 扫描方式 | 提取内容 |
|---|---|---|
| **Dubbo** | `ServiceBean` / `@DubboService`（兼容 2.7 与 3.x） | 接口全限定名、version、group、方法签名 |
| **gRPC** | `BindableService` Bean | 服务名、方法、streaming 形态、消息类型 |
| **SOFARPC-TR** | `@SofaService` Bean（零编译依赖，反射式） | 接口、方法签名、uniqueId、bindings |
| **tRPC** | `@TRpcService` Bean（零编译依赖，反射式） | 接口、方法签名、name/version/group |

### API 响应结构

```json
{
  "restApis": [...],
  "webSocketApis": [...],
  "rpcApis": [...]
}
```

协议不存在时为空数组。

## 配置参考

### API 展示

```yaml
archimedes:
  api:
    enabled: true          # API 展示总开关
    base-path: /archimedes # 端点根路径
    ui-enabled: true       # 是否挂载内置 UI
    base-packages: []      # 非空时只扫描指定包前缀下的 Controller
```

### 链路追踪

```yaml
archimedes:
  trace:
    enabled: true              # 链路追踪总开关
    header-name: X-Trace-Id    # 透传/回写 traceId 的请求头
    response-header: true      # 是否在响应头回写 traceId
    mdc-key: traceId           # traceId 在 MDC 中的 key
    span-id-key: spanId        # spanId 在 MDC 中的 key
    use-project-trace-id: false # true=信任宿主自有 Filter 已写入的 MDC traceId
```

**解析优先级**：用户 `TraceIdResolver` Bean → 请求头（`header-name`）→ 宿主 MDC（需 `use-project-trace-id=true`）→ `TraceIdGenerator` 生成。

**自定义生成算法**：注册自己的 `TraceIdGenerator` Bean（如雪花算法）即可替换默认 UUID（`@ConditionalOnMissingBean` 让位）。

**精准清理**：请求结束只回滚本请求写入的 MDC 键并恢复旧值，不调用 `MDC.clear()`，宿主自有 MDC 上下文零破坏。

### 跨线程 traceId 传递

Spring 容器管理的线程池**引入即用自动覆盖**（`archimedes.trace.propagation.enabled=false` 可关闭）：

- `ThreadPoolTaskExecutor` Bean（含 `@Async` 线程池）— 自动注入 `MdcTaskDecorator`
- `ExecutorService` / `ScheduledExecutorService` / `Executor` 接口 Bean — 自动替换为 MDC 传递包装器
- `TaskScheduler`（`@Scheduled` 定时任务）不包装 — 定时任务不源于请求上下文

**自动化盲区**（CompletableFuture 默认 commonPool、自建裸线程池等）使用一行式手动包装：

```java
CompletableFuture.supplyAsync(MdcWrappers.wrap(() -> doWork()));
executor.submit(MdcWrappers.wrap(runnable));
```

> **注意**：若宿主定义了任意 `Executor` Bean（如启用 STOMP 后的通道执行器），Spring Boot 的 `applicationTaskExecutor` 会退避，`@Async` 退化为 `SimpleAsyncTaskExecutor`（无法自动传递）。Archimedes 启动时会检测该场景并打 WARN，按提示定义名为 `taskExecutor` 的 Bean 即可。

### 链路日志采集

```yaml
archimedes:
  log:
    capture:
      enabled: true                # 日志采集总开关
      max-entries: 10000           # 全局日志条目上限
      max-entries-per-trace: 500   # 单链路日志条目上限
```

Logback 环境下引入即用：结构化采集 Appender 编程式挂载 root logger（与日志输出格式完全解耦），只采集 MDC 含 traceId 的日志。超限按最老链路整体淘汰。

**生产持久化**：注册自定义 `LogStore` Bean（如 Elasticsearch 实现）接入，内存实现自动让位。

### 日志配置兜底

宿主没有任何 logback 配置时，Archimedes 在日志系统初始化前注入内置配置（控制台 + 滚动文件双输出，pattern 含 `[traceId] [spanId]`）。宿主有自有配置时**完全不介入**。

```yaml
archimedes:
  log:
    fallback-enabled: true   # 兜底总开关
    pattern: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] [%X{spanId:-}] %-5level %logger{36} - %msg%n"
    path: ./logs             # 滚动文件目录
    max-history: 30          # 保留天数
    max-file-size: 100MB     # 单文件大小
```

### 配置中心

```yaml
archimedes:
  config:
    enabled: true              # 配置中心总开关（关闭后端点与相关 Bean 均不装配）
    hot-refresh-enabled: true  # 热更新开关（false = 只读模式，update 端点返回 403）
    sensitive-keys: password,secret,token,credential,key  # 敏感键关键字（contains 匹配，配置后整体替换）
```

- **配置查询**：`GET {base-path}/config` 按 Environment 优先级枚举全部可枚举属性源，标注每项配置的来源；命中敏感关键字的值统一脱敏为 `******`。
- **热更新**：`POST {base-path}/config/update` 写入最高优先级动态属性源，`environment.getProperty()` 立即生效；prefix 命中的 `@ConfigurationProperties` Bean 自动**原地重绑定**（构造器绑定的不可变 Bean 无法刷新，跳过并 WARN）。
- **事件通知**：每次变更发布 `ArchimedesConfigChangedEvent`（携带变更 key 集合），宿主监听该事件即可感知配置变化（如刷新自维护的配置缓存）。本依赖不对 Spring Cloud 提供支持、不发布其环境变更事件。
- **边界声明**：动态覆盖仅存于内存，**不持久化**——应用重启后恢复为底层配置源原值；不写回 application.properties 文件。热更新适用于开发/联调场景，生产环境建议关闭或配合安全框架收敛访问。

### 数据库监控

```yaml
archimedes:
  sql:
    enabled: true             # SQL 监控总开关（关闭后数据源不包装、端点不装配）
    slow-sql-millis: 1000     # 慢 SQL 阈值（毫秒），耗时 >= 阈值判为慢
    max-history-size: 500     # 最近执行 / 慢 SQL 环形缓冲各自上限
    capture-parameters: true  # 是否采集绑定参数（可能含敏感数据，可关）
    exclude-beans: []         # 按 Bean 名排除不包装的数据源（逃生口）
    max-sql-stats: 1000       # 去重 SQL 聚合条目上限（防动态拼接 SQL 撑爆内存）
```

- **零侵入接入**：`BeanPostProcessor` 自动将容器内全部 `DataSource` Bean 包装为标准 JDBC 代理（类似 Druid Filter/p6spy），拦截 Statement/PreparedStatement 执行。
- **SQL 明细**：语句（空白归一化）、绑定参数、耗时、类型（QUERY/UPDATE/BATCH/EXECUTE）、行数（查询经 ResultSet 计数、更新取影响行数）、异常信息，以及执行线程的 **traceId**——UI 中可一键跳转该链路的日志时间线。
- **聚合统计**：按「数据源 + 归一化 SQL」聚合执行次数/总耗时/平均/最大/失败次数；慢 SQL 单独成列。
- **连接池指标**：`PoolMetricsContributor` SPI；classpath 存在 HikariCP（Boot 默认池）时自动输出活跃/空闲/等待/总连接与池配置，其它连接池可自行实现 SPI 接入。
- **多数据源**：按 Bean 名区分，端点与 UI 全部纳入。
- **边界声明**：仅监控 JDBC `DataSource`（R2DBC 不支持）；包装后注入方拿到的是代理对象——`unwrap()` 全量透传保证 Boot/actuator/Flyway 等经 Wrapper 链可达原生实现，但**按 `HikariDataSource` 具体类型注入**的代码需用 `exclude-beans` 排除；监控数据存内存，重启清零。

## 扩展点

| 扩展点 | 机制 | 用途 |
|---|---|---|
| `TraceIdGenerator` | `@ConditionalOnMissingBean` | 替换默认 UUID 生成算法（如雪花 ID） |
| `TraceIdResolver` | `@ConditionalOnMissingBean` | 自定义 traceId 解析策略 |
| `LogStore` | `@ConditionalOnMissingBean` | 替换内存日志存储（如 ES 持久化） |

所有扩展点均通过注册同类型 Bean 接入，框架默认实现自动让位。

## 构建与运行

```bash
# 构建全部模块（需 JDK 21）
mvn clean install

# 运行示例应用
mvn -pl example -am spring-boot:run          # SB3 示例 → http://localhost:8080/archimedes
mvn -pl example-boot2 -am spring-boot:run    # SB2.7 示例 → http://localhost:8081/archimedes
mvn -pl example-all -am spring-boot:run      # 全功能演示 → http://localhost:8082/archimedes
```

## 内置 UI

访问 `{base-path}` 即可打开 API Explorer 页面，功能包括：

- **协议分 Tab** — REST / WebSocket / RPC / TR / Config / DB / Trace Logs
- **文本过滤** — 按路径、方法名等快速搜索
- **REST 方法筛选** — 按 GET / POST / PUT / DELETE 等 HTTP 方法过滤
- **RPC 协议筛选** — 按 Dubbo / gRPC / SOFA_TR / TRPC 过滤
- **在线调试** — REST 条目展开后按契约预填参数发起请求，响应携带 trace 头时一键跳转链路日志查询
- **请求/响应结构展示** — 字段说明表与示例 JSON 预填
- **配置中心** — 按属性源分组浏览全量配置（动态覆盖高亮）、搜索过滤、行内编辑热更新、一键移除覆盖恢复原值
- **数据库监控** — 连接池状态卡片、SQL 统计表、最近执行/慢 SQL 列表（traceId 可点击联动链路日志）、手动/自动刷新
