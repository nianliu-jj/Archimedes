# Tasks: contract-schema-enrichment

- [x] 1. core 模型：`FieldInfo`（name/type/required/description/array/children）；`ApiInfo` 增 `requestBodySchema`/`responseSchema`；`ParamInfo` 增 `description`（新增 5 参构造，4 参委托保持兼容）
- [x] 2. core 解析器：`TypeSchemaResolver`（包装解包、集合/Map/数组、POJO 字段树、深度/循环保护、枚举值、FQCN 反射读 Swagger v2/v3 + Jackson + validation 注解、防御式降级）
- [x] 3. core 接线：`AbstractRestApiScanner.buildApiInfo` 计算双 schema；`toParamInfo` 读参数说明（两栈共享）
- [x] 4. core 测试：`TypeSchemaResolverTest`（嵌套/集合/Map/枚举/循环/包装解包/JsonProperty 改名/JsonIgnore）+ 同 FQCN 桩注解（Swagger v3 Schema/Parameter、jakarta NotNull；Jackson 用真实注解）驱动说明与必填提取
- [x] 5. UI：示例 JSON 生成预填（exampleFromSchema）、请求/响应字段表（嵌套缩进）、Query/Header/路径参数说明展示；无 schema 优雅回退
- [x] 6. e2e：双 starter EndToEndTest 增 POST+DTO 端点断言 requestBodySchema/responseSchema（含 array 标记与 ResponseEntity 解包）在列
- [x] 7. `mvn clean install` 全绿（148 测试：core 66 + sb2 41 + sb3 41）+ 真机验证（example jar：POST /api/users 输出字段树、responseSchema=UserResponse、params 带 description、UI 含 Request/Response Fields 标记）
- [x] 8. 文档：README 契约小节 + 功能清单（Slice 15）+ 规格同步归档
