# 请求参数 / 响应描述注解体系设计

> 日期：2026-07-09
> 作者：nianliu-jj（brainstorming 产出，用户已确认设计）

## 一、背景与目标

当前 `@ApiField`（`@Target({PARAMETER, FIELD})`）同时承担两职：描述方法参数、描述 POJO 字段；
参数「是否必填」来自绑定注解（`@RequestParam(required=...)` 等）。本次将职责拆清并补齐响应描述：

1. 新增 `@ApiParam`：专用于请求参数说明，**可标注在参数前或方法上**；方法上可用 `@ApiParams` 容器统一管理，`@ApiParam` 设为 `@Repeatable(ApiParams.class)`，可直接连写多个。
2. 参数「是否必填」改由 `@ApiParam#required` 决定（未标注时回退绑定注解）。
3. `@ApiField` **保留但收窄**为只描述 POJO 字段。
4. 新增 `@ApiResponse`：描述响应结果，**按 HTTP 状态码分条、可重复**，容器 `@ApiResponses`。

约束沿用既有决策：接口/参数/字段描述**只认自有注解**；零额外依赖；`archimedes-core` 以 `--release 8` 编译、无 servlet 依赖。

## 二、注解定义

全部位于 `io.github.nianliu.archimedes.annotation`，`@Retention(RUNTIME)` + `@Documented`。

### `@ApiParam`
- `@Target({METHOD, PARAMETER})`，`@Repeatable(ApiParams.class)`
- 字段：
  - `String name() default ""` —— 参数名。方法级标注时必须与参数名匹配才命中；参数级标注时可省略。
  - `String value() default ""` —— 参数说明。
  - `boolean required() default false` —— 是否必填（页面必填列的来源）。
  - `String example() default ""` —— 示例值（供 UI 在线调试预填）。

### `@ApiParams`
- `@Target(METHOD)`
- `ApiParam[] value()` —— 方法级参数说明集合（`@ApiParam` 的重复容器）。

### `@ApiResponse`
- `@Target(METHOD)`，`@Repeatable(ApiResponses.class)`
- 字段：
  - `int code() default 200` —— HTTP 状态码。
  - `String description() default ""` —— 响应说明。
  - `Class<?> type() default Void.class` —— 响应体类型；非 `Void.class` 时解析其字段结构树。
  - `String example() default ""` —— 响应示例。

### `@ApiResponses`
- `@Target(METHOD)`
- `ApiResponse[] value()` —— 响应声明集合（`@ApiResponse` 的重复容器）。

### `@ApiField`（调整）
- `@Target(FIELD)`（**移除 `PARAMETER`**），字段维持 `value` / `required` / `example` 不变。
- 语义收窄：仅用于 POJO 字段结构树的说明与必填。

> `@Repeatable` 容器 `@Target(METHOD)` 是被重复注解目标集合的子集，合法：单个 `@ApiParam` 仍可标在参数上，多个 `@ApiParam` 在方法上由 Java 自动聚合进 `@ApiParams`。

## 三、绑定与取值规则

### 参数说明命中
- **参数级**：`@ApiParam` 直接标在参数前 —— 作用于该参数本身，`name` 可省略、命中不依赖 `name`。
- **方法级**：`@ApiParam`（重复连写或写进 `@ApiParams`）—— **`name` 必须等于解析出的参数名**才命中该参数。
- **优先级**：同一参数被参数级与方法级同时命中时，**参数级优先**（就近原则）。

### 必填来源
- 命中 `@ApiParam` → `ParamInfo.required = @ApiParam#required`。
- 未命中任何 `@ApiParam` → 回退现状：`@RequestParam/@PathVariable/@RequestHeader/@RequestBody#required`（`OTHER` 来源仍为 false）。

### 说明与示例
- `ParamInfo.description` / `ParamInfo.example` 命中时取 `@ApiParam` 的 `value` / `example`；未命中为空串。
- validation 校验线（`@Pattern/@Size/...` → `ParamInfo.validation`）保持不变，与描述正交。

### 参数名解析
沿用 `AbstractRestApiScanner` 现有逻辑：绑定注解显式 `name/value` → 方法签名参数名（`DefaultParameterNameDiscoverer`）→ `argN` 兜底。方法级 `@ApiParam#name` 与此解析结果比对。

## 四、模型变更

### 新增 `ResponseInfo`（`io.github.nianliu.archimedes.model`）
- `int code` —— 状态码。
- `String description` —— 说明。
- `String type` —— 响应体类型展示串（简名；`Void` 记为空/省略）。
- `FieldInfo schema` —— 响应体字段树（`type` 为 `Void.class` 时为 null）。

### `ApiInfo`
- 新增 `List<ResponseInfo> responses`（默认空列表，getter/setter）。
- **保留** `responseSchema`（由返回类型自动推导的成功响应结构）——与 `responses` 互补：前者自动、后者注解声明。

### `ParamInfo`
- 形状不变，仅 `required/description/example` 的取值来源改为 `@ApiParam`（见第三节）。

## 五、扫描 / 解析改动

### `TypeSchemaResolver`
- `paramDescription(Annotation[])` / `paramExample(Annotation[])`：改读参数级 `@ApiParam`。
- 新增 `paramApiParam(Method method, Annotation[] paramAnnotations, String paramName)`：先取参数级 `@ApiParam`，无则在方法级 `@ApiParams`/重复 `@ApiParam` 中按 `name` 匹配；返回命中的 `@ApiParam` 或 null。
- 新增 `responses(Annotation[] methodAnnotations)`：读取 `@ApiResponse`/`@ApiResponses` → `List<ResponseInfo>`，`type != Void.class` 时复用 `resolve(type)` 出字段树。
- `fieldDescription/fieldRequired`：仍读 `@ApiField`（`FIELD` 语义不变）。

### `AbstractRestApiScanner`
- `toParamInfo`：改为接收所属 `Method`（或其注解）；按第三节规则解析参数级/方法级 `@ApiParam`，决定 `required/description/example`。
- `buildApiInfo`：`info.setResponses(TypeSchemaResolver.responses(method.getAnnotations()))`。

> RPC/WebSocket 模型不含参数描述位（`RpcMethodInfo` 只存参数类型），`@ApiParam`/`@ApiResponse` **仅作用于 REST**；RPC/WS 的方法级描述仍走 `@ApiDoc`（不变）。

## 六、前端（`archimedes-ui/index.html`）

- 参数表「必填」列取值随后端自动正确（无需前端改动逻辑，仅数据来源变化）。
- REST 卡片 try-it/详情区新增「响应」区块：遍历 `responses` 列出 `code + description`（+ `type` 字段树表，若有 `schema`），与自动 `responseSchema` 并列展示；`responses` 为空时不渲染该区块。

## 七、演示与测试

### example-all 迁移
- `@ApiField`-on-参数 → `@ApiParam`：`OrderController`（status/size/orderNo/idempotencyKey）、`OrderDbController`（id/item/amount）。
- POJO 字段上的 `@ApiField`（`CreateOrderRequest`/`OrderItemPayload`/`OrderResponse`）**保留**。
- 补 `@ApiResponse` 演示：至少一个端点声明 200 + 错误码（如 404/400）响应，含 `type`。
- 补方法级 `@ApiParam`/`@ApiParams` 演示（与参数级并存，验证优先级）。

### 测试
- `TypeSchemaResolverTest`：参数级/方法级 `@ApiParam` 命中、优先级、`@ApiResponse` 解析（含 `type` 字段树）、`@ApiField` 仅字段生效。
- `RestApiScannerTest`：必填回退（无 `@ApiParam` 时取绑定注解 required）、命中时取 `@ApiParam#required`；`responses` 填充。
- 双端 e2e（sb2/sb3）+ example-all e2e：`/apis` 输出参数描述/必填/示例、`responses` 列表正确。

### 文档
- 更新 `docs/功能清单与任务列表.md`（描述注解条目 + 锁定决策表）：新增 `@ApiParam/@ApiParams/@ApiResponse/@ApiResponses`，`@ApiField` 收窄说明。

## 八、非目标（YAGNI）

- 不为 RPC/WebSocket 增加参数级描述模型。
- 不做多语言/i18n 响应描述。
- 不引入任何第三方注解依赖（Swagger 等）。
- `@ApiResponse#type` 仅解析字段结构，不做校验/序列化联动。

## 九、兼容性与影响

- **破坏性**：`@ApiField` 移除 `PARAMETER` 目标 —— 现有把 `@ApiField` 标在参数上的代码将编译报错。仓库内仅 example-all 有此用法，随本次迁移到 `@ApiParam`。对外部消费方为语义明确的破坏性变更，需在文档/变更说明中标注。
- **非破坏性**：未标注 `@ApiParam` 的参数行为不变（必填回退绑定注解）；`responses` 默认空列表，旧前端忽略即可。
