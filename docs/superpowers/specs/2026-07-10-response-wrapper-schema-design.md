# 统一响应包装体展示设计

> 日期：2026-07-10
> 作者：nianliu-jj（brainstorming 产出，用户已确认设计）

## 一、背景与目标

许多项目用 `ResponseBodyAdvice`（`@RestControllerAdvice`）在运行时把 Controller 的返回值统一包进一个响应壳，例如：

```java
public class ResultVo {
    private int code;      // 状态码
    private String msg;    // 状态信息
    private Object data;   // 真实返回对象
}
```

Archimedes 的 REST 扫描是**静态**的：`AbstractRestApiScanner.buildApiInfo` 用
`TypeSchemaResolver.resolve(method.getGenericReturnType())` 生成 `responseSchema`，
解析的是**方法返回类型**（即包装壳里的内层 `data`），看不到运行时才套上的 `ResultVo` 外壳。
于是页面展示的响应结构与客户端实际收到的 JSON 不一致。

**目标**：让 Archimedes 能感知这层统一包装，把 `responseSchema` 呈现为**完整的包装体**
（外壳字段 + `data` 处嵌入方法真实返回类型的字段树），并对不应被包装的接口正确豁免。

约束沿用既有决策：零额外依赖；`archimedes-core` 以 `--release 8` 编译、无 servlet 依赖；
描述只认自有注解；前端单文件零构建。

## 二、感知方式：配置声明

静态扫描无法可靠推断运行时 `beforeBodyWrite` 的任意逻辑（字符串特例、异常包装等），
故采用**配置声明**：用户在 `application.yml` 告知包装类与 data 字段。

新增配置（前缀 `archimedes.api.response-wrapper`）：

```yaml
archimedes:
  api:
    response-wrapper:
      enabled: true                             # 是否启用；默认 false
      wrapper-class: com.bugpool.leilema.ResultVo   # 包装类 FQCN；为空则不启用
      data-field: data                          # data 所在字段名，默认 "data"
```

在 `ArchimedesApiProperties` 新增内部类 `ResponseWrapper`：

```java
public static class ResponseWrapper {
    private boolean enabled = false;      // 默认关闭
    private String wrapperClass = "";     // 包装类 FQCN；为空则不启用
    private String dataField = "data";    // data 字段名
    // getter/setter：getWrapperClass/setWrapperClass 等
}
```

> 配置键说明：`class` 是 Java 关键字，故属性名用 `wrapperClass`、配置键为
> `archimedes.api.response-wrapper.wrapper-class`（Spring Boot relaxed binding 将
> kebab-case 键映射到驼峰属性），避开关键字歧义、键名语义清晰。文档与示例统一用
> `wrapper-class`。

启用判定：`enabled == true && wrapperClass 非空 && 该类能被 Class.forName 加载`。
任一不满足 → 视为未启用，`responseSchema` 原样输出（零影响）。

## 三、核心逻辑：包装体结构组装

新增 `ResponseWrapperResolver`（`io.github.nianliu.archimedes.scanner.schema` 包），
职责单一：把内层 schema 包进外壳。签名：

```java
/**
 * 若配置了响应包装类，把内层 responseSchema 嵌入包装类的 data 字段位置，返回完整包装体 FieldInfo；
 * 未配置/加载失败/无 data 字段时原样返回 innerSchema。
 */
public FieldInfo wrap(FieldInfo innerSchema, Method method);
```

流程：

1. **未启用** → 直接返回 `innerSchema`。
2. **豁免命中**（见第四节）→ 直接返回 `innerSchema`。
3. 反射加载包装类 → `TypeSchemaResolver.resolve(wrapperClass)` 得到外壳字段树
   （`code`/`msg`/`data`…）。
4. 在外壳字段树的**直接子字段**中找名为 `dataField`（默认 `data`）的节点：
   - 找到 → 保留该节点的 `name`、`description`、`required`、`validation`、`enumValues`，
     把其 `type`/`array`/`children` **替换为 `innerSchema` 的对应值**（即把 data 的结构换成方法真实返回类型的结构）。`innerSchema` 为 null（void 内层）时不替换，data 节点保持包装类原样（通常是 `Object`）。
   - 未找到 → 记 WARN（`data-field '{}' 在包装类 {} 中不存在`），返回 `innerSchema`（降级，不硬失败）。
5. 返回改造后的外壳字段树作为最终 `responseSchema`。

前端**零改动**：`responseSchema` 仍是一棵 `FieldInfo` 树，既有 `schemaTableHtml`/
`exampleFromSchema` 能直接渲染完整包装体。

### 接入点
`AbstractRestApiScanner.buildApiInfo`：

```java
FieldInfo inner = TypeSchemaResolver.resolve(method.getGenericReturnType());
info.setResponseSchema(responseWrapperResolver.wrap(inner, method));
```

`ResponseWrapperResolver` 由扫描器持有（构造注入 `ArchimedesApiProperties`）。
Servlet 与 Reactive 两个子类共享（经 `AbstractRestApiScanner` 基类）。

## 四、豁免规则（不包装）

以下三种，`responseSchema` 保持内层结构、不套壳：

1. **`@NoApiWrapper` 标注**（新增注解，`@Target({METHOD, TYPE})`，`@Retention(RUNTIME)`）：
   标在方法上豁免该方法；标在类上豁免整个 Controller。语义等同用户示例里的
   `@NotControllerResponseAdvice`。
2. **返回类型是包装类或其子类**：`wrapperClass.isAssignableFrom(returnRawClass)`——
   对应用户 `supports()` 里的 `isAssignableFrom(ResultVo.class)`，接口本就直接返回壳、不二次包装。
3. **返回类型是 `ResponseEntity`**（`org.springframework.http.ResponseEntity`）：
   常用于自定义状态码、绕过 advice。按 FQCN 判断（core 已编译依赖 spring-web，无新增依赖）。

判断在 `wrap()` 内完成；豁免命中即返回 `innerSchema`。

## 五、边界与降级

- 只作用于 REST 的 `responseSchema`；请求体 schema、参数、RPC、WebSocket、`@ApiResponse` 均不受影响。
- **`@ApiResponse` 默认不套壳**：`@ApiResponse#type` 的字段树是用户显式声明的完整响应，所见即所得，不自动包装。
- `dataField` 在包装类中不存在 → WARN + 降级返回内层。
- 内层为 void（`resolve` 返回 null）→ data 节点保留包装类原样，不替换。
- 包装类解析复用 `TypeSchemaResolver` 现有深度上限 / 循环保护 / 异常降级。
- 包装类加载失败（FQCN 错误 / 不在 classpath）→ 视为未启用，返回内层（不硬失败）。
- watch 签名：`responseSchema` 已纳入 `ArchimedesWatchController.signature`，包装体变化自然被感知，无需额外改动。

## 六、演示与测试

### example-all
- 新增 `ResultVo`（code/msg/data）+ 一个 `ResponseBodyAdvice`（`@RestControllerAdvice`）演示真实包装。
- 配置 `archimedes.api.response-wrapper.wrapper-class=...ResultVo`、`data-field=data`、`enabled=true`。
- 一个被包装端点（普通返回 POJO/List）+ 一个 `@NoApiWrapper` 豁免端点，页面对照。

### 测试
- `ResponseWrapperResolverTest`（core）：
  - 包装组装：data 节点子结构被替换为内层类型、外壳其余字段保留；
  - 三类豁免（`@NoApiWrapper` 方法级/类级、返回包装类、返回 `ResponseEntity`）各返回内层；
  - `data-field` 缺失 → 降级返回内层 + WARN；
  - void 内层 → data 节点原样；
  - 未启用 / 包装类加载失败 → 返回内层。
- `ArchimedesApiPropertiesTest` 或装配测试：`response-wrapper.*` 绑定正确。
- 双端 e2e（sb2/sb3 或 example-all）：`/apis` 里被包装端点 `responseSchema` 顶层 type = `ResultVo`、
  含 `code`/`msg`/`data` 字段，`data` 子树为方法真实返回类型；`@NoApiWrapper` 端点 `responseSchema` 为内层类型。

### 文档
- `docs/功能清单与任务列表.md`：契约增强条目补充「统一响应包装体展示」；配置项表补 `response-wrapper.*`。

## 七、非目标（YAGNI）

- 不自动探测 `ResponseBodyAdvice` bean（`beforeBodyWrite` 任意逻辑，静态不可靠）。
- 不支持多个/条件包装类（仅单一全局包装）。
- 不改请求体 schema，不改 `@ApiResponse` 语义。
- 不做包装类的运行时校验（仅静态结构展示）。

## 八、兼容性

- 纯新增能力，默认关闭（`enabled=false`），不配置则**行为完全不变**，向后兼容。
- 新增 `@NoApiWrapper` 注解、`ResponseWrapper` 配置内部类、`ResponseWrapperResolver` 类，
  均为新增，不改动现有模型字段与既有注解语义。
