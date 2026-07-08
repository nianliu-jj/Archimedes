# 设计：自有接口描述注解体系 + 前端 soybean 风格重构

- 日期：2026-07-08
- 状态：已确认（brainstorming 完成，待用户审阅书面 spec）
- 关联需求：用户要求「实现一套注解用于描述接口/请求参数/响应参数/字段，描述需在页面展示；参考 soybean-admin 优化前端」

## 一、背景与现状

Archimedes 当前的接口描述信息**全部经 `TypeSchemaResolver` 用 FQCN 字符串反射读取宿主的 Swagger v3/v2、Jackson、validation 注解**（零编译依赖，按优先级列表匹配）：

- 接口摘要/描述：`@Operation`(v3) / `@ApiOperation`(v2)
- 模块分组：`@Tag`(v3) / `@Api`(v2)
- 参数说明：`@Parameter`(v3) / `@ApiParam`(v2)
- 字段说明：`@Schema`(v3) / `@ApiModelProperty`(v2) / `@JsonPropertyDescription`
- 必填：validation `@NotNull/@NotBlank/@NotEmpty` + Swagger `required`

前端是**烤进 jar 的单文件 `index.html`**（ES5、零构建、`__ARCHIMEDES_API_URL__` 占位符注入、启动即用），当前为顶部横向 Tab 布局（REST/WS/RPC/TR/Config/DB/Trace）。

本设计新增一套 Archimedes **自有描述注解**取代 Swagger 描述提取，并把前端重构为 soybean-admin 视觉语言（左 sider + 顶栏）。

## 二、目标 / 非目标

**目标：**
- 提供 `@ApiModule` / `@ApiDoc` / `@ApiField` 三个自有 RUNTIME 注解，描述模块/接口/参数/字段。
- 覆盖 REST + RPC（Dubbo/gRPC/SOFA_TR/tRPC）+ WebSocket 接口类型。
- 描述信息在前端强化展示（接口卡片 summary/desc、模块菜单、字段说明列）。
- 前端重构为 soybean 视觉语言，仍是单文件、零构建、ES5。

**非目标：**
- 不引入前端构建链（Vue/Vite/NaiveUI 仅作视觉参考，不引技术栈）。
- 不保留对 Swagger/Jackson **描述**注解的反射读取（"只认自有注解"——已用 Swagger 的宿主需迁移）。
- 不移除 validation 校验规则提取（`@Pattern/@Size/@Min/@Max` → 前端表单校验，与描述正交，保留）。
- 不改变现有功能行为（6 Tab、try-it、trace 联动、配置中心、DB 监控、SSE、认证栏零回归）。

## 三、后端注解体系

### 3.1 注解清单（`io.github.nianliu.archimedes.annotation` 包，core 模块）

| 注解 | @Target | @Retention | 属性 |
|---|---|---|---|
| `@ApiModule` | TYPE | RUNTIME | `value`(=name 别名, 默认 "")、`name`(默认 "")、`description`(默认 "") |
| `@ApiDoc` | METHOD | RUNTIME | `summary`(=value 别名, 默认 "")、`value`(默认 "")、`description`(默认 "")、`deprecated`(默认 false) |
| `@ApiField` | {PARAMETER, FIELD} | RUNTIME | `value`(说明, 默认 "")、`required`(默认 false)、`example`(默认 "") |

- `value` 别名规则：`@ApiModule("订单")` 等价 `name="订单"`；`@ApiDoc("创建订单")` 等价 `summary="创建订单"`；`@ApiField("商品ID")` 等价 `value="商品ID"`。取值时 name/summary 优先，空则回退 value。
- 注解自带于 Archimedes，宿主引入任一 starter 即可用，零传递依赖。

### 3.2 接入点（改动聚焦于 `TypeSchemaResolver`）

将 `TypeSchemaResolver` 中的 Swagger/Jackson 描述 FQCN **替换为自有注解 FQCN**：

- `DESCRIPTION_ANNOTATIONS`（字段说明）→ 仅 `io.github.nianliu.archimedes.annotation.ApiField#value`
- `PARAM_DESCRIPTION_ANNOTATIONS`（参数说明）→ 仅 `@ApiField#value`
- `operationSummary()` → 读 `@ApiDoc#summary`（回退 value）
- `operationDescription()` → 读 `@ApiDoc#description`
- `tagName()` → 读 `@ApiModule#name`（回退 value；再回退 Controller 类简名去 Controller 后缀）
- `tagDescription()` → 读 `@ApiModule#description`
- `fieldRequired()` → 读 `@ApiField#required`（移除 Swagger required 分支；validation `@NotNull` 等**不再**作为 required 来源，required 只认 `@ApiField`）
- `extractValidation()` / `paramValidation()` → **保持不变**（前端表单校验线，正交保留）

注：`@ApiDoc#deprecated=true` 与现有 `@Deprecated`/类级 `@Deprecated` 取或（任一为真即 deprecated）。

### 3.3 RPC / WebSocket 接入

- 模型扩展：`RpcApiInfo` 新增 `description`（服务级，读服务接口类型上的 `@ApiModule#description`）、`RpcMethodInfo` 新增 `description`（方法级，读方法上的 `@ApiDoc`）、`WsApiInfo` 新增 `description`（读 handler 类/方法上的 `@ApiDoc`）。
- 扫描器接入：
  - 四个 RPC 扫描器（`DubboRpcScanner`/`GrpcRpcScanner`/`SofaTrRpcScanner`/`TrpcRpcScanner`）自省服务接口/方法时，反射读取 `@ApiModule`/`@ApiDoc` 填充 description。反射式扫描器（SOFA/tRPC）继续走"零编译依赖 + FQCN 字符串"路子读自有注解（自有注解在 core，可直接引用类型，无需字符串）。
  - WS 扫描器（`SpringWebSocketHandlerScanner`/`StompMappingScanner` + 两 starter 的 `ServerEndpointScanner`）在 handler 类/方法上读 `@ApiDoc` 填充 description。
- 兼容：description 缺省空串，序列化恒为字符串，前端无 description 时不渲染副行。

### 3.4 测试与示例迁移

- `example-all`：移除 Swagger v3 注解演示，改用 `@ApiModule`/`@ApiDoc`/`@ApiField`（保留 validation 注解演示前端校验）。`swagger-annotations` 依赖可移除。
- core 测试：`TypeSchemaResolverTest`、`RestApiScannerTest`、schema 相关测试中的 Swagger 桩注解（`io.swagger.v3.oas.annotations.*` 测试源）迁移为自有注解断言。RPC/WS 扫描测试补 description 断言。
- 新增 `annotation` 包注解的单测（属性默认值、value 别名回退）。

## 四、前端重构（soybean 视觉语言）

### 4.1 布局骨架（左 sider + 顶栏）

```
┌─────────┬──────────────────────────────────────┐
│ 🧭 Arch │  [🔍 全局搜索]              🌙 主题   │  顶栏 56px + 柔和阴影
├─────────┼──────────────────────────────────────┤
│ ▸REST 12│  ┌── Order 模块 · 订单管理 ────────┐ │  卡片按 @ApiModule 分组
│  WS   3 │  │ 创建订单  [POST] /api/orders [Try]│ │  summary 主标题
│  RPC  4 │  │ 下单并返回单号（@ApiDoc.desc）    │ │  description 副行灰字
│  TR   2 │  └──────────────────────────────────┘ │
│  Config │  展开：参数 / 请求体 / 响应体三段      │  字段表含"说明"列(@ApiField)
│  DB     │                                        │
└─────────┴──────────────────────────────────────┘
```

- sider 220px：一级为协议（REST/WS/RPC/TR/Config/DB），带计数徽标；REST/RPC 下可按 `@ApiModule` 模块二级锚点。
- 顶栏 56px：品牌区 + 全局搜索 + 主题切换（🌙）。
- 内容区底色 `rgb(247,250,252)`，卡片白底圆角 6 + 柔和阴影。

### 4.2 视觉令牌（取自 soybean `themeSettings`）

- 主色 `#646cff`；info/success/warning/error = `#2080f0`/`#52c41a`/`#faad14`/`#f5222d`
- 圆角 6px；卡片阴影 `0 1px 2px rgb(0,21,41,.08)`；sider 阴影 `2px 0 8px 0 rgb(29,35,41,.05)`
- HTTP 方法彩色 chip：GET 绿 / POST 蓝 / PUT 橙 / DELETE 红 / PATCH 紫
- 深/浅主题：CSS 变量驱动，顶栏切换，localStorage 记忆（默认浅色）
- 字体：优先系统 UI 字体栈（-apple-system, Segoe UI, ...）

### 4.3 描述信息展示强化（核心诉求）

- 接口卡片：主标题 = `@ApiDoc.summary`（空则 handler 方法名）；副行灰字 = `description`；`@Deprecated` 划线标记。
- sider 模块菜单 = `@ApiModule`（name + 计数徽标），点击滚动/筛选到该组。
- 参数表 / 字段表：强化"说明"列读 `@ApiField.value`；`example` 用于 try-it 输入预填。
- RPC/WS 卡片：展示服务级/方法级 `description`。

### 4.4 功能保留（零回归清单）

现有全部功能平移进新骨架，仅重排视觉：6 Tab、REST try-it（行内展开、参数预填、trace 响应头联动 jumpToTrace）、schema 字段表 + 示例 JSON 预填、前端表单校验、配置中心（分组/搜索/编辑/覆盖高亮/移除）、DB 监控（池卡片/统计/慢 SQL/自动刷新/traceId 联动）、SSE 热监听、认证栏（BEARER/BASIC/API_KEY/SA_TOKEN）。

### 4.5 实现约束

- 产物仍是单个 ES5 `index.html`（`__ARCHIMEDES_API_URL__` 占位符注入不变；`id="tabs"` 等 e2e 锚点断言按需调整并同步更新双 starter 的 e2e 锚点）。
- 用 `frontend-design` + `ui-ux-pro-max` skill 指导排版/间距/组件质感。
- 因文件较大，分步替换（骨架+CSS 令牌 → 各 Tab 渲染函数迁移 → 描述展示强化），每步真机验证。

## 五、交付与分片

整体一个 spec，实现拆两个 slice，各走 OpenSpec（propose→apply→archive）流程、双端测试全绿、中文提交：

- **Slice A（后端）**：`annotation` 包 3 注解 + `TypeSchemaResolver` 接入切换 + RPC/WS 模型与扫描器接入 + 测试/example 迁移。
- **Slice B（前端）**：`index.html` soybean 风格重构 + 描述展示强化 + 双 starter e2e 锚点同步。

Slice A 先行（前端展示依赖后端字段就绪）。

## 六、风险与权衡

| 风险 | 缓解 |
|---|---|
| 移除 Swagger 描述提取导致已用 Swagger 的宿主丢描述 | 已确认边界（BREAKING）；README/文档明确迁移指引；validation 校验线保留 |
| 单文件 index.html 重构面大、易漏迁移功能 | 分步替换 + 每步真机验证 + 零回归清单逐项核对；保留 e2e 锚点断言 |
| RPC 反射扫描器读自有注解 | 自有注解在 core，扫描器可直接引用类型（比 Swagger 字符串反射更稳），无新依赖 |
| e2e 锚点（如 `id="tabs"`）随重构变化 | 重构后同步更新双 starter e2e 的锚点断言 |

## 七、锁定决策（brainstorming 问答结论）

| 决策点 | 结论 |
|---|---|
| 注解形态 | 分粒度多注解（`@ApiModule`/`@ApiDoc`/`@ApiField`） |
| 与 Swagger 关系 | 只认自有注解（移除 Swagger/Jackson 描述提取；validation 校验线保留） |
| 前端载体 | 单文件 index.html 重构，零构建，ES5 |
| 前端布局 | 左 sider + 顶栏（soybean 中后台观感） |
| 注解覆盖面 | REST + RPC + WebSocket |
