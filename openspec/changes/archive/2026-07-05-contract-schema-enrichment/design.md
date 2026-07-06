# Design: contract-schema-enrichment

## Context

字段"描述"在运行时唯一可得的来源是注解（javadoc 编译后不可见）。宿主项目最常见的三种注解体系：Swagger v3（springdoc）、Swagger v2（springfox）、Jackson。core 不能引入这些编译依赖（保持零依赖原则），但可用与 SOFA/tRPC 扫描相同的 **FQCN 字符串 + 反射读取** 模式。

## Goals / Non-Goals

- Goals：BODY 参数与返回类型的字段树（名/类型/必填/说明/集合标记/嵌套）；参数级说明；UI 示例 JSON 预填与字段表；两栈同享。
- Non-Goals：不做完整 OpenAPI 规范生成（非目标，Swagger 生态已有）；不做运行时请求/响应**实例**抓包（"监听获取"落为契约级结构提取——实例级抓包涉及流复制、脱敏与体积问题，且录入辅助只需要结构）；不解析 javadoc；不做泛型变量深度绑定（未解析的类型变量按 Object 叶子处理）。

## Decisions

### D1：结构模型 = 递归 FieldInfo 树
`FieldInfo { name, type(简名展示串), required, description, array(集合/数组标记), children }`。根节点 name 为空串、type 为解包后的类型简名。标量根（如返回 String）children 为空；`void`/`Void` 返回 schema 为 null。

### D2：解析器为纯静态工具 `TypeSchemaResolver`，防御式
- 解包链（按原始类 FQCN 判断，避免对 reactor 等的编译依赖）：`ResponseEntity`/`HttpEntity`/`Optional`/`CompletableFuture`/`CompletionStage`/`Callable`/`Mono` 解 T；`Flux`/`Collection`/数组 → `array=true` 并对元素递归；`Map<K,V>` 展示 `Map<K简名,V简名>` 并对 V 递归为 children。
- 叶子判定：基本型/包装/`String`/`Number`/时间日期（`java.time.*`/`Date`）/`BigDecimal` 等，以及兜底"`java.`/`javax.`/`jakarta.` 开头且非集合"；枚举为叶子且 description 自动补"枚举: A / B / C"。
- POJO：沿继承链 `getDeclaredFields`（class 到 `java.` 前缀为止），跳过 static/transient/synthetic 与 `@JsonIgnore`；`@JsonProperty` 覆盖名。
- 安全阀：深度上限 4；同一路径上类重现即判循环，落叶子并注记"(递归引用)"；一切异常吞掉返回 null（契约主体不受影响）。

### D3：注解读取 = FQCN 匹配 + 反射取属性（零编译依赖）
- 字段说明：`io.swagger.v3.oas.annotations.media.Schema#description` → `io.swagger.annotations.ApiModelProperty#value` → `com.fasterxml.jackson.annotation.JsonPropertyDescription#value`，取第一个非空。
- 字段必填：validation `@NotNull/@NotBlank/@NotEmpty`（javax + jakarta 双 FQCN）或 Swagger required（v3 `requiredMode()==REQUIRED` 或弃用的 `required()`，v2 `required()`）。
- 参数说明：`io.swagger.v3.oas.annotations.Parameter#description` → `io.swagger.annotations.ApiParam#value`。
- 宿主没有这些注解库时：`annotationType().getName()` 永不匹配 → 全部为空，零影响。

### D4：接线在 AbstractRestApiScanner（两栈免费共享）
`buildApiInfo` 内：取首个 BODY 参数的 genericParameterType → `requestBodySchema`；方法 genericReturnType → `responseSchema`。`toParamInfo` 增读参数说明。JSON 纯新增字段，向后兼容。

### D5：UI 端生成示例 JSON，而非服务端
UI 按 schema 递归生成：叶子按 type 串启发（数字类→0、Boolean→false、其余→""）、`array=true` 包 `[]`、对象递归 children——服务端不加"example"端点，保持 JSON 契约单一职责。try-it 面板新增两张可折叠字段表（请求字段/响应字段：字段、类型、必填、说明，嵌套字段缩进展示）；Query/Header/路径变量输入框以灰字展示说明。

## Risks / Trade-offs

- 反射字段与 Jackson 实际序列化视图可能有出入（getter-only 属性、@JsonInclude 等）——录入辅助场景可接受，防御式降级保证不误导核心契约。
- 泛型变量（如 `Result<T>` 未绑定处）按 Object 叶子处理，不做全量泛型解析。

## Migration Plan

纯新增字段与 UI 增强，宿主升级即得，无迁移。

## Open Questions

无。
