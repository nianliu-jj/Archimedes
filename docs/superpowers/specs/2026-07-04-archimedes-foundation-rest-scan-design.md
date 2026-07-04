# Archimedes 设计文档 · 切片一：地基 + REST 接口扫描

- **日期**：2026-07-04
- **状态**：已确认，待写实现计划
- **范围**：整个平台的第一条端到端竖切
- **产物名**：`archimedes`（`io.github.nianliu:archimedes`）

---

## 1. 背景与整体愿景

Archimedes 是一个 **Spring Boot Starter**，目标是"引入即用的 API 可观测性"：任意 Maven / Spring Boot / Spring Cloud 项目引入依赖后，无需额外配置即可获得两大能力——

- **能力 A · 接口契约发现**：扫描并展示 REST / WebSocket / RPC(Dubbo、gRPC) / TR(tRPC) 接口。
- **能力 B · 全链路日志追踪**：可配置 traceId、跨线程 MDC 传递、日志配置管理（内置 logback fallback）、按 traceId 采集与查询。

完整需求见 `docs/项目需求.md`（文档中产品代号 "Contra"，本项目统一改用 `archimedes`）。

这是一个平台级需求，包含多个相互独立的子系统，**不适合塞进单个 spec**。因此拆分为多条竖切，每条各自走「设计 → 计划 → 实现」循环。本文件只覆盖**切片一**。

## 2. 本切片的目标与非目标

### 目标
1. 搭好 **starter 地基**：自动装配、配置体系、跨 Boot 2.x/3.x 的注册与依赖策略。
2. **REST 接口扫描**：发现应用中所有 Spring MVC `@RequestMapping` 系接口，转成统一模型。
3. **JSON 端点**：用一个 `@RestController` 暴露扫描结果。
4. **极简内置前端**：一个静态 HTML 页面，表格列出所有接口并支持关键字搜索。
5. 验证核心命题：**引入依赖即零配置生效**。

### 非目标（明确排除，留给后续切片）
- WebFlux / 响应式 `@RestController` 扫描
- WebSocket / RPC(Dubbo、gRPC) / TR(tRPC) 扫描
- 端点鉴权与权限控制（交给用户自己的 Spring Security）
- REST 接口在线调试
- 完整 Swagger-UI 风格前端（分 Tab、分类、契约详情）
- **全部日志追踪能力（能力 B 整体）**

## 3. 已确认的关键决策

| 决策点 | 结论 | 理由 |
|---|---|---|
| 首个范围 | 地基 + REST 扫描 + JSON + 极简页面 | 最薄端到端竖切，最早验证"引入即用" |
| Spring Boot 版本 | **同时支持 2.x 和 3.x** | 面向存量(2.x)与新(3.x)项目，作为要发中央仓库的库需通用 |
| 本切是否多模块 | **否，单模块** | REST 扫描走 Spring 稳定注解层，不碰 `javax↔jakarta` 的 Servlet API；做日志追踪(Servlet Filter)时再引入多模块 |
| 暴露方式 | **普通 `@RestController`** | 零额外依赖、跨版本无差异、最简单；安全交给用户 security |
| 前端 | **随包内置极简静态页** | 第一版即"看得见"，成本低 |
| 扫描机制 | **方案三：复用 `RequestMappingHandlerMapping`** | 见下节 |
| 命名 | `archimedes` 贯穿包名/配置前缀/端点 | 与产物名一致 |

### 3.1 扫描机制：为什么选方案三

需求文档给了两个方案，我们采用第三个：

- **方案一（文档推荐）**：取 `@RestController` Bean，自己反射 `@RequestMapping`/`@GetMapping`。❌ 等于重写 Spring 的路径合并逻辑（类级+方法级拼接、组合注解、继承、多路径/多方法），边界 case 易错。
- **方案二**：JavaParser 解析源码 AST。❌ 复杂，且拿不到运行时真实映射。
- **方案三 ✅**：直接读 Spring 启动时已算好的 `RequestMappingHandlerMapping.getHandlerMethods()`，返回 `Map<RequestMappingInfo, HandlerMethod>`。路径合并、HTTP 方法、consumes/produces 全部现成且正确；相关类型（`RequestMappingInfo`、`HandlerMethod`、`org.springframework.web.bind.annotation.*`）在 Spring 5/6 中包名一致，跨 Boot 2/3 稳定。

**原则**：把"接口发现"交还给 Spring 本身，Archimedes 只做「读取 → 转统一模型 → 暴露」。

## 4. 架构

### 4.1 模块与依赖策略
- **单模块** JAR。
- `spring-webmvc`、`spring-boot-autoconfigure` 标 `<optional>true</optional>`，**继承用户项目版本**，从而自动适配 2.x/3.x。
- **不**引入 actuator、websocket。
- 双份自动装配注册文件，实现跨版本自动生效：
  - `META-INF/spring.factories` —— key `org.springframework.boot.autoconfigure.EnableAutoConfiguration`（Boot 2 读取，Boot 3 忽略该 key）。
  - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` —— 每行一个类名（Boot 3 读取，Boot 2 忽略此文件）。
- **待实现时敲定的小细节**：自动装配类的注解写法。`@AutoConfiguration` 需 Boot ≥ 2.7；若要兼容更低 2.x，则用 `@Configuration(proxyBeanMethods = false)`。默认锚定 **Boot 2.7+ / 3.x**，用 `@AutoConfiguration`；如需更低版本再退回 `@Configuration`。

### 4.2 组件划分
```
io.github.nianliu.archimedes
├── config/
│   ├── ArchimedesAutoConfiguration      // @ConditionalOnWebApplication(SERVLET) + @ConditionalOnClass(RequestMappingHandlerMapping)
│   │                                     // @ConditionalOnProperty("archimedes.api.enabled", matchIfMissing=true)
│   │                                     // 装配 scanner / controller / properties
│   └── ArchimedesApiProperties          // @ConfigurationProperties("archimedes.api")
├── scanner/
│   └── RestApiScanner                    // 读 RequestMappingHandlerMapping → List<ApiInfo>，结果缓存
├── model/
│   ├── ApiInfo
│   └── ParamInfo
└── web/
    └── ArchimedesApiController           // @RestController：GET {base-path}/apis 返回 JSON；GET {base-path} 返回内置 HTML（当 ui-enabled）
src/main/resources/
└── archimedes-ui/index.html              // 极简表格页面（原生 JS，零构建）。放在 static/ 之外，仅由 controller 在
                                          // {base-path} 下服务，避免被静态资源处理器以固定 URL 重复暴露；页面用相对地址
                                          // fetch "apis"，与 base-path 解耦
```

每个单元职责单一、边界清晰：
- **RestApiScanner**：输入 = 容器里的 `RequestMappingHandlerMapping` Bean(s)；输出 = `List<ApiInfo>`；只读，不改变应用行为；内部缓存扫描结果。
- **ArchimedesApiController**：输入 = HTTP 请求；输出 = JSON / 静态页；依赖 RestApiScanner。
- **ArchimedesApiProperties**：纯配置载体。
- **ArchimedesAutoConfiguration**：装配上述 Bean，处理条件与开关。

## 5. 数据模型

```
ApiInfo:
  controllerClass : String         // 全限定类名
  handlerMethod   : String         // 方法名
  httpMethods     : List<String>   // GET/POST/...（RequestMappingInfo.methodsCondition；空集合 = 未限定，视为全部）
  paths           : List<String>   // 合并后的完整路径
  params          : List<ParamInfo>
  returnType      : String         // 保留泛型，如 "java.util.List<com.example.User>"
  consumes        : List<String>   // 可选
  produces        : List<String>   // 可选
  deprecated      : boolean        // 方法/类是否标注 @Deprecated（顺带，低成本）

ParamInfo:
  name     : String
  source   : enum { QUERY, PATH, BODY, HEADER, FORM, OTHER }
  type     : String                // 参数类型
  required : boolean
```

### 提取细节
- **路径**：优先 `RequestMappingInfo.getPathPatternsCondition()`（PathPattern，Boot 3 默认 / Boot 2 可选），回退 `getPatternsCondition()`（Ant String，Boot 2 默认）。⚠️ **这是跨 2.x/3.x 的关键点**——两个方法在两版本都存在，取值二选一非空。
- **HTTP 方法**：`RequestMappingInfo.getMethodsCondition().getMethods()`。
- **参数**：遍历 `HandlerMethod.getMethodParameters()`，按 `@RequestParam` / `@PathVariable` / `@RequestBody` / `@RequestHeader` 判定 `source`（无上述注解归 `OTHER`）；`required` 取自注解；`type` 取参数类型全限定名。
- **返回类型**：`method.getGenericReturnType().getTypeName()`，保留泛型。

### 已知限制
- **参数名**：依赖目标项目用 `-parameters` 编译，或注解里显式写了名字（如 `@RequestParam("id")`）。都没有时降级为 `arg0/arg1/...`。文档与页面需说明此限制。

## 6. 数据流

```
应用启动
  └─ Spring 构建 RequestMappingHandlerMapping（context refresh 后就绪）
      └─ 首次访问 {base-path}/apis
          └─ RestApiScanner 懒扫描并缓存（AtomicReference，零启动开销）
              └─ ArchimedesApiController 返回 List<ApiInfo> JSON
                  └─ 内置页面（{base-path}）相对 fetch "apis" → 渲染可搜索表格
```

- **扫描时机**：懒加载——首次访问端点时扫描一次并缓存。避开启动期 Bean 就绪顺序问题，零启动开销。（备选：`ApplicationReadyEvent` 预热；本切采用懒加载。）

## 7. 配置项（`archimedes.api.*`）

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `base-path` | `/archimedes` | 端点根路径；JSON = `{base-path}/apis`，UI = `{base-path}` |
| `ui-enabled` | `true` | 是否在 `{base-path}` 挂载内置页面 |
| `base-packages` | 空（全部） | 只扫描指定包下的 Controller |

默认从结果中排除 Spring 内建 `/error` 与 Archimedes 自身端点，避免噪声与自引用。

## 8. 错误处理与边界

- **非 Web 应用**：`@ConditionalOnWebApplication(SERVLET)` 不装配，静默跳过。
- **无 `RequestMappingHandlerMapping`**：返回空列表，不抛异常。
- **单条解析失败**：跳过该 handler 并记 `warn`，不影响其余结果。
- **只读保证**：全过程不修改容器状态、不改变请求行为。
- **缓存一致性**：本切结果视为启动后不变；动态刷新留待后续。

## 9. 测试策略（TDD 推进）

- **单元测试 · RestApiScanner**：构造/注入含样例 Controller 的 `RequestMappingHandlerMapping`，断言 `ApiInfo` 各字段（路径合并、HTTP 方法、参数 source、泛型返回类型、`@Deprecated`）。
- **单元测试 · 跨版本路径**：分别模拟 `pathPatternsCondition` 与 `patternsCondition` 两种取值，验证回退逻辑。
- **集成测试 · `@SpringBootTest`**：起最小应用（含样例 Controller），打 `{base-path}/apis` 断言 JSON 结构与内容；验证 `/error` 与自身端点被排除。
- **跨版本 CI**：Boot 2 / Boot 3 双版本构建矩阵，作为后续增强；本切至少在一个版本跑通，并在文档中说明跨版本处理点（路径条件、双注册文件、optional 依赖）。

## 10. 后续切片（决定顺序，非本切范围）

1. **[本切] 地基 + REST 扫描 + JSON + 极简页**
2. WebFlux / 响应式 REST 扫描
3. WebSocket 扫描（`@ServerEndpoint` / `WebSocketHandlerRegistry`）
4. RPC 扫描（Dubbo / gRPC）
5. TR / tRPC 扫描
6. 完整前端（分 Tab、搜索筛选、契约详情、在线调试）
7. **能力 B**：全链路日志追踪（traceId 生成/过滤器 → 跨线程 MDC 传递 → logback 配置管理 → 按 traceId 采集查询 → 追踪前端）——此处首次引入 Servlet Filter，届时把地基拆成多模块以兼容 javax/jakarta。
