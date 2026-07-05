# Archimedes

引入即用的 Spring Boot API 可观测性依赖：自动扫描宿主应用的接口契约（当前支持 REST 与 WebSocket，后续将支持 Dubbo / gRPC / SOFARPC-TR / tRPC），通过内置端点与 UI 页面展示。

## 已支持的接口契约

- **REST**：`@RestController` 的路径、HTTP 方法、参数（含来源与 required）、返回类型、consumes/produces、`@Deprecated` 标记
- **WebSocket**（三种形态，宿主未使用 WebSocket 时零影响）：
  - `@ServerEndpoint` 注解端点（SB2=javax / SB3=jakarta；须注册为 Spring Bean，容器 SCI 直接注册的端点不在覆盖内）
  - `WebSocketConfigurer` 注册的 handler（含 SockJS 标记）
  - STOMP：握手端点、`@MessageMapping`、`@SubscribeMapping` 目的地

`GET {base-path}/apis` 返回分组结构：`{"restApis": [...], "webSocketApis": [...]}`（协议不存在时为空数组，后续协议按新增字段扩展）。

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
    enabled: true          # 总开关，false 时不注册任何 Bean
    base-path: /archimedes # 端点根路径
    ui-enabled: true       # 是否挂载内置 UI
    base-packages: []      # 非空时只扫描这些包前缀下的 Controller
```

## 构建与示例

```bash
mvn clean install                # 构建全部模块（JDK 21）
mvn -pl example -am spring-boot:run        # SB3 示例，http://localhost:8080/archimedes
mvn -pl example-boot2 -am spring-boot:run  # SB2.7 示例，http://localhost:8081/archimedes
```
