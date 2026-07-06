# contract-schema Specification

## Purpose

定义 REST 契约的请求/响应体结构提取语义（字段树：名/类型/必填/说明/集合标记/嵌套）、注解说明的零依赖读取规则，以及内置 UI 的录入辅助与响应结构展示要求。

## Requirements

### Requirement: 请求体与响应体结构提取
REST 契约 SHALL 对首个 `@RequestBody` 参数类型与方法返回类型进行结构解析，在契约条目上输出 `requestBodySchema` 与 `responseSchema` 字段树（字段名、类型、是否必填、说明、集合标记、嵌套子字段）；解析 SHALL 解包常见包装类型（ResponseEntity/HttpEntity/Optional/CompletableFuture/Mono/Flux/集合/数组/Map）；SHALL 有深度上限与循环引用保护；解析异常 SHALL 降级为空 schema 而不影响契约主体；`void` 返回与无 BODY 参数时对应字段 SHALL 为 null。Servlet 与 Reactive 两栈 SHALL 同等生效。

#### Scenario: 嵌套对象结构在列
- **WHEN** 某 POST 端点的 `@RequestBody` 为含嵌套对象与集合字段的 POJO
- **THEN** 该条目的 `requestBodySchema` 含对应字段树，集合字段带集合标记且元素字段为其子节点

#### Scenario: 包装类型解包
- **WHEN** 端点返回 `ResponseEntity<List<User>>` 或 `Mono<User>`
- **THEN** `responseSchema` 为解包后 User 的字段树（List 情形根节点带集合标记）

#### Scenario: 循环引用安全
- **WHEN** 类型存在自引用字段（如树形结构）
- **THEN** 解析在重现处落叶子节点并终止递归，不抛错不死循环

### Requirement: 字段与参数说明零依赖提取
字段说明 SHALL 按优先级反射读取 Swagger v3 `@Schema(description)`、Swagger v2 `@ApiModelProperty(value)`、Jackson `@JsonPropertyDescription`；字段必填 SHALL 叠加 validation `@NotNull/@NotBlank/@NotEmpty`（javax/jakarta 双系）与 Swagger required 标记；枚举字段 SHALL 自动列出可选值；`@JsonProperty` SHALL 覆盖字段名、`@JsonIgnore` SHALL 跳过字段。Query/Header/路径参数 SHALL 反射读取 `@Parameter(description)`/`@ApiParam(value)` 输出 `description`。以上均为 FQCN 反射读取，core SHALL 不引入 Swagger/validation 编译依赖，宿主未使用相应注解时说明为空且零影响。

#### Scenario: Swagger 注解宿主
- **WHEN** 宿主 DTO 字段标注 `@Schema(description = "用户名")`
- **THEN** 对应 FieldInfo 的 description 为"用户名"

#### Scenario: 无注解宿主零影响
- **WHEN** 宿主未引入任何 Swagger/validation 注解库
- **THEN** 契约照常输出，description 为空，无异常无告警

### Requirement: UI 录入辅助与响应结构展示
内置 UI 的 REST 调试面板 SHALL：按 `requestBodySchema` 自动生成示例 JSON 预填请求体；提供"请求字段"与"响应字段"表（字段/类型/必填/说明，嵌套缩进展示）；Query/Header/路径参数输入处 SHALL 展示参数说明。无 schema 时 SHALL 优雅回退到原有行为。

#### Scenario: 示例 JSON 预填
- **WHEN** 展开含 `requestBodySchema` 的 POST 端点调试面板
- **THEN** 请求体文本框预填按字段树生成的示例 JSON（数字 0、布尔 false、字符串空串、集合含单元素）

#### Scenario: 响应字段速览
- **WHEN** 展开含 `responseSchema` 的端点调试面板
- **THEN** 面板展示响应字段表，含字段名、类型与说明
