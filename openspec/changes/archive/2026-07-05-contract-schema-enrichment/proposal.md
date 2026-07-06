# Proposal: contract-schema-enrichment

## Why

当前 REST 契约只含参数的 name/source/type/required 与返回类型的字符串签名，用户在 UI 调试接口时：请求体只能凭空手写 JSON；响应体拿到后不知道字段含义。需求：**监听获取接口的请求参数与响应体结构**，让用户在界面测试接口时能便捷录入请求参数，并快速理解响应体内容与字段描述含义。

## What Changes

1. **core 新增类型结构解析器 `TypeSchemaResolver`**：对 BODY 参数类型与方法返回类型做反射解析，产出字段树 `FieldInfo`（字段名/类型/是否必填/说明/是否集合/子字段）：
   - 解包常见包装：`ResponseEntity`/`HttpEntity`/`Optional`/`CompletableFuture`/`Mono`/`Flux`/集合/数组/Map；
   - POJO 沿继承链反射字段（跳过 static/transient/synthetic），深度限制 + 循环引用保护；
   - **字段说明零编译依赖反射读取**（沿用 SOFA/tRPC 的 FQCN 反射模式）：Swagger v3 `@Schema(description)`、Swagger v2 `@ApiModelProperty(value)`、Jackson `@JsonPropertyDescription`；必填叠加 `@NotNull/@NotBlank/@NotEmpty`（javax/jakarta 双系）；枚举自动列出可选值；
   - Jackson `@JsonProperty` 覆盖字段名、`@JsonIgnore` 跳过。
2. **契约模型扩展**：`ApiInfo` 增加 `requestBodySchema` 与 `responseSchema`（FieldInfo 树，无则为 null）；`ParamInfo` 增加 `description`（反射读 Swagger `@Parameter(description)`/`@ApiParam(value)`）。Servlet 与 Reactive 两栈经共享骨架同时获得。
3. **UI try-it 面板升级**：
   - Query/Header/路径参数输入框旁展示参数说明；
   - 请求体按 `requestBodySchema` **自动生成示例 JSON 预填**（替代空 `{}`），并提供"请求字段"表（字段/类型/必填/说明）；
   - 新增"响应字段"表（字段/类型/说明），发送前即可了解响应结构与字段含义。

## Capabilities

### New
- `contract-schema`：请求/响应体结构提取语义、JSON 暴露形态与 UI 录入辅助要求。

## Impact

- 代码：core（新模型 + 解析器 + 骨架接线 + ParamInfo 扩展）、UI 单文件、双 starter e2e 断言补充。
- 兼容性：纯新增字段（旧消费者忽略未知字段即可）；无配置变更；host 未用 Swagger/validation 注解时说明为空、其余信息照常。
- 风险：反射解析防御式实现（异常吞并降级为 null schema），不影响原有契约输出。
