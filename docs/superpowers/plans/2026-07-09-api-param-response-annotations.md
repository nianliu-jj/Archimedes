# 请求参数 / 响应描述注解体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 拆分参数/字段描述职责——新增 `@ApiParam`(可标方法/参数,可重复)+`@ApiParams` 描述请求参数、新增 `@ApiResponse`(按状态码可重复)+`@ApiResponses` 描述响应，`@ApiField` 收窄为只描述 POJO 字段，参数必填改由 `@ApiParam` 决定（未标注回退绑定注解）。

**Architecture:** 注解位于 `archimedes-core` 的 `annotation` 包；`TypeSchemaResolver` 集中承载注解读取逻辑；`AbstractRestApiScanner` 消费之填充 `ParamInfo`/新增 `ApiInfo.responses`；仅作用于 REST（RPC/WS 方法级描述仍走 `@ApiDoc`）；前端 `index.html` 增「响应」区块。

**Tech Stack:** Java 8 字节码（core `--release 8`）、Spring MVC/WebFlux `HandlerMethod` 反射、JUnit 5 + AssertJ、单文件 ES5 前端、Maven 多模块。

## Global Constraints

- 回答与注释用中文；每个**新建** Java 文件类头加 javadoc，`@author nianliu-jj`，`@since 2026-07-09`。
- 关键代码加详细中文注释；关键节点加日志（本特性以注解静态读取为主，日志按需）。
- `archimedes-core` 以 `--release 8` 编译，**不得** `import javax.servlet.*`/`jakarta.servlet.*`，不引入任何第三方注解依赖。
- 描述**只认自有注解**；validation 校验线（`ParamInfo.validation`/`FieldInfo.validation`）保持不变、与描述正交。
- 注解全部 `@Retention(RUNTIME)` + `@Documented`。
- 每个 Task 结束提交，中文 commit message。用 `mvn` 运行（工程内 shim → maven 3.9.16，JDK 21）。

---

### Task 1: `@ApiParam` + `@ApiParams` 注解

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiParam.java`
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiParams.java`

**Interfaces:**
- Produces: `@interface ApiParam { String name() default ""; String value() default ""; boolean required() default false; String example() default ""; }`（`@Target({METHOD,PARAMETER})`, `@Repeatable(ApiParams.class)`）；`@interface ApiParams { ApiParam[] value(); }`（`@Target(METHOD)`）。

- [ ] **Step 1: 写 `ApiParam.java`**

```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述一个 REST 请求参数。可标注在参数前，或标注在方法上（此时 {@link #name()} 必须与参数名匹配才命中）。
 * <p>设为 {@link Repeatable}，方法上可直接连写多个，由 Java 自动聚合进 {@link ApiParams}。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiParams.class)
public @interface ApiParam {

    /** 参数名。标在方法上时必须等于参数名才命中；标在参数前时可省略。 */
    String name() default "";

    /** 参数说明。 */
    String value() default "";

    /** 是否必填（页面必填列的来源）。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
```

- [ ] **Step 2: 写 `ApiParams.java`**

```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级参数说明容器：统一管理标注在方法上的多个 {@link ApiParam}（{@link ApiParam} 的重复容器）。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiParams {

    /** 方法上的参数说明集合。 */
    ApiParam[] value();
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl archimedes-core compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiParam.java archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiParams.java
git commit -m "feat: 新增 @ApiParam/@ApiParams 注解（描述请求参数，可标方法/参数，可重复）"
```

---

### Task 2: `@ApiResponse` + `@ApiResponses` 注解

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiResponse.java`
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiResponses.java`

**Interfaces:**
- Produces: `@interface ApiResponse { int code() default 200; String description() default ""; Class<?> type() default Void.class; String example() default ""; }`（`@Target(METHOD)`, `@Repeatable(ApiResponses.class)`）；`@interface ApiResponses { ApiResponse[] value(); }`（`@Target(METHOD)`）。

- [ ] **Step 1: 写 `ApiResponse.java`**

```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述一条 REST 响应（按 HTTP 状态码分条）。设为 {@link Repeatable}，方法上可连写多个描述不同状态码。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiResponses.class)
public @interface ApiResponse {

    /** HTTP 状态码。 */
    int code() default 200;

    /** 响应说明。 */
    String description() default "";

    /** 响应体类型；非 {@code Void.class} 时解析其字段结构树展示。 */
    Class<?> type() default Void.class;

    /** 响应示例。 */
    String example() default "";
}
```

- [ ] **Step 2: 写 `ApiResponses.java`**

```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 响应声明容器：统一管理标注在方法上的多个 {@link ApiResponse}（{@link ApiResponse} 的重复容器）。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiResponses {

    /** 方法上的响应声明集合。 */
    ApiResponse[] value();
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl archimedes-core compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiResponse.java archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiResponses.java
git commit -m "feat: 新增 @ApiResponse/@ApiResponses 注解（按状态码描述响应，可重复）"
```

---

### Task 3: `ResponseInfo` 模型 + `ApiInfo.responses`

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ResponseInfo.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ApiInfo.java`
- Test: `archimedes-core/src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java`

**Interfaces:**
- Produces: `ResponseInfo{ int getCode()/setCode; String getDescription()/set; String getType()/set; FieldInfo getSchema()/set }`；`ApiInfo.getResponses(): List<ResponseInfo>` / `setResponses(List<ResponseInfo>)`（默认空列表）。

- [ ] **Step 1: 写 `ResponseInfo.java`**

```java
package io.github.nianliu.archimedes.model;

/**
 * 单条 REST 响应契约（对应一个 HTTP 状态码），来自自有 {@code @ApiResponse} 注解。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
public class ResponseInfo {

    /** HTTP 状态码。 */
    private int code;
    /** 响应说明。 */
    private String description;
    /** 响应体类型展示串（简名）；Void 时为 null。 */
    private String type;
    /** 响应体字段结构树；type 为 Void 或解析失败时为 null。 */
    private FieldInfo schema;

    public ResponseInfo() {
    }

    public ResponseInfo(int code, String description, String type, FieldInfo schema) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.schema = schema;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FieldInfo getSchema() {
        return schema;
    }

    public void setSchema(FieldInfo schema) {
        this.schema = schema;
    }
}
```

- [ ] **Step 2: 给 `ApiInfo` 加 `responses` 字段**

在 `ApiInfo.java` 字段区（`tagDescription` 之后）追加：

```java
    /** 声明的响应契约列表（来自自有 @ApiResponse；默认空列表）。 */
    private java.util.List<ResponseInfo> responses = java.util.Collections.emptyList();
```

在末尾 `getTagDescription/setTagDescription` 之后追加 getter/setter：

```java
    public java.util.List<ResponseInfo> getResponses() {
        return responses;
    }

    public void setResponses(java.util.List<ResponseInfo> responses) {
        this.responses = responses == null ? java.util.Collections.<ResponseInfo>emptyList() : responses;
    }
```

- [ ] **Step 3: 写失败测试（追加到 `ApiModelTest`）**

在 `ApiModelTest` 中新增（若无该测试类的 import，补 `import io.github.nianliu.archimedes.model.*;` 已在同包无需 import）：

```java
    @org.junit.jupiter.api.Test
    void apiInfoResponsesDefaultEmptyAndSettable() {
        ApiInfo info = new ApiInfo();
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).isEmpty();

        ResponseInfo r = new ResponseInfo(404, "订单不存在", null, null);
        info.setResponses(java.util.List.of(r));
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(info.getResponses().get(0).getCode()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(info.getResponses().get(0).getDescription()).isEqualTo("订单不存在");

        info.setResponses(null); // null 归一为空列表
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).isEmpty();
    }
```

- [ ] **Step 4: 运行测试**

Run: `mvn -q -pl archimedes-core test -Dtest=ApiModelTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ResponseInfo.java archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ApiInfo.java archimedes-core/src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java
git commit -m "feat: 新增 ResponseInfo 模型与 ApiInfo.responses 字段"
```

---

### Task 4: `TypeSchemaResolver` 新增 `@ApiParam` 解析 + `@ApiResponse` 提取

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolver.java`
- Test: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolverTest.java`

**Interfaces:**
- Consumes: `ApiParam`/`ApiParams`/`ApiResponse`/`ApiResponses`（Task 1/2）、`ResponseInfo`（Task 3）、既有 `resolve(Type)`。
- Produces:
  - `public static ApiParam paramApiParam(java.lang.reflect.Method method, Annotation[] paramAnnotations, String paramName)` —— 命中的 `@ApiParam` 或 null（参数级优先，方法级按 name 匹配）。
  - `public static java.util.List<ResponseInfo> responses(java.lang.reflect.Method method)` —— 解析 `@ApiResponse` 列表（type 非 Void 出字段树）。

- [ ] **Step 1: 写失败测试（追加到 `TypeSchemaResolverTest`）**

先补 import：

```java
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
import io.github.nianliu.archimedes.model.ResponseInfo;
import java.lang.reflect.Method;
import java.util.List;
```

在 `Holder` 类外新增样例类型与方法，并加测试：

```java
    static class Sample {
        // 参数级 @ApiParam
        void paramLevel(@ApiParam(value = "状态过滤", required = true, example = "PAID") String status) { }

        // 方法级 @ApiParam（重复连写），name 与参数名匹配
        @ApiParam(name = "id", value = "订单号", required = true, example = "O-1")
        @ApiParam(name = "size", value = "分页大小")
        void methodLevel(String id, int size) { }

        // 参数级优先于方法级
        @ApiParam(name = "code", value = "方法级说明")
        void precedence(@ApiParam(value = "参数级说明") String code) { }

        @ApiResponse(code = 200, description = "成功", type = OrderItem.class)
        @ApiResponse(code = 404, description = "订单不存在")
        void responses() { }
    }

    private static Method method(String name, Class<?>... args) throws Exception {
        return Sample.class.getDeclaredMethod(name, args);
    }

    @Test
    void resolvesParamLevelApiParam() throws Exception {
        Method m = method("paramLevel", String.class);
        ApiParam p = TypeSchemaResolver.paramApiParam(m, m.getParameters()[0].getAnnotations(), "status");
        assertThat(p).isNotNull();
        assertThat(p.value()).isEqualTo("状态过滤");
        assertThat(p.required()).isTrue();
        assertThat(p.example()).isEqualTo("PAID");
    }

    @Test
    void resolvesMethodLevelApiParamByName() throws Exception {
        Method m = method("methodLevel", String.class, int.class);
        ApiParam id = TypeSchemaResolver.paramApiParam(m, new java.lang.annotation.Annotation[0], "id");
        assertThat(id).isNotNull();
        assertThat(id.value()).isEqualTo("订单号");
        assertThat(id.required()).isTrue();
        ApiParam size = TypeSchemaResolver.paramApiParam(m, new java.lang.annotation.Annotation[0], "size");
        assertThat(size.value()).isEqualTo("分页大小");
        // 不匹配的名字返回 null
        assertThat(TypeSchemaResolver.paramApiParam(m, new java.lang.annotation.Annotation[0], "nope")).isNull();
    }

    @Test
    void paramLevelWinsOverMethodLevel() throws Exception {
        Method m = method("precedence", String.class);
        ApiParam p = TypeSchemaResolver.paramApiParam(m, m.getParameters()[0].getAnnotations(), "code");
        assertThat(p.value()).isEqualTo("参数级说明");
    }

    @Test
    void resolvesApiResponses() throws Exception {
        List<ResponseInfo> responses = TypeSchemaResolver.responses(method("responses"));
        assertThat(responses).hasSize(2);
        ResponseInfo ok = responses.stream().filter(r -> r.getCode() == 200).findFirst().orElseThrow();
        assertThat(ok.getDescription()).isEqualTo("成功");
        assertThat(ok.getType()).isEqualTo("OrderItem");
        assertThat(ok.getSchema()).isNotNull();
        ResponseInfo notFound = responses.stream().filter(r -> r.getCode() == 404).findFirst().orElseThrow();
        assertThat(notFound.getType()).isNull();
        assertThat(notFound.getSchema()).isNull();
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=TypeSchemaResolverTest`
Expected: 编译失败（`paramApiParam`/`responses` 未定义）

- [ ] **Step 3: 在 `TypeSchemaResolver` 实现两方法**

补 import（文件顶部 import 区）：

```java
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
import io.github.nianliu.archimedes.model.ResponseInfo;
import java.lang.reflect.Method;
```

删除已不再需要的旧方法 `paramDescription(Annotation[])` 与 `paramExample(Annotation[])`（读 `@ApiField`，Task 5 起不再调用）。在 `paramValidation` 附近新增：

```java
    /**
     * 解析某个 REST 参数命中的 {@code @ApiParam}：参数级优先，其次方法级（按 name 与参数名匹配）。
     * <p>方法级读取用 {@code method.getAnnotationsByType(ApiParam.class)}，
     * {@code @Repeatable} 会自动把 {@code @ApiParams} 容器与连写的多个 {@code @ApiParam} 一并展开。
     *
     * @param method          所属处理方法
     * @param paramAnnotations 该参数上的注解数组
     * @param paramName        解析出的参数名（页面展示名）
     * @return 命中的 {@code @ApiParam}，无则 null
     */
    public static ApiParam paramApiParam(Method method, Annotation[] paramAnnotations, String paramName) {
        // 参数级优先：直接标在参数前的 @ApiParam 就近生效，不依赖 name 匹配
        ApiParam direct = find(paramAnnotations, ApiParam.class);
        if (direct != null) {
            return direct;
        }
        // 方法级：按 name 与参数名匹配
        if (method != null) {
            for (ApiParam p : method.getAnnotationsByType(ApiParam.class)) {
                if (paramName != null && paramName.equals(p.name())) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * 解析方法上的 {@code @ApiResponse} 列表（{@code @Repeatable}，含 {@code @ApiResponses} 容器）。
     * type 非 {@code Void.class} 时复用 {@link #resolve(Type)} 出字段树；解析异常降级为无 schema。
     *
     * @param method 处理方法
     * @return 响应契约列表，无声明时空列表
     */
    public static List<ResponseInfo> responses(Method method) {
        List<ResponseInfo> result = new ArrayList<>();
        if (method == null) {
            return result;
        }
        for (ApiResponse r : method.getAnnotationsByType(ApiResponse.class)) {
            Class<?> type = r.type();
            boolean hasType = type != null && type != Void.class && type != void.class;
            FieldInfo schema = hasType ? resolve(type) : null;
            String typeName = hasType ? type.getSimpleName() : null;
            result.add(new ResponseInfo(r.code(), r.description(), typeName, schema));
        }
        return result;
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=TypeSchemaResolverTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolver.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolverTest.java
git commit -m "feat: TypeSchemaResolver 支持 @ApiParam 参数解析与 @ApiResponse 提取"
```

---

### Task 5: 扫描器切换参数描述源为 `@ApiParam` + 填充 responses（含 example-all 迁移）

> 本任务是「参数描述来源」的原子切换：core 扫描器、core 测试样例、example-all 演示与 e2e 必须一并改，否则全量构建变红。`@ApiField` 目标暂不收窄（Task 6 处理），此刻参数上 `@ApiField`/`@ApiParam` 语法均合法。

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/web/ArchimedesWatchController.java`（signature 纳入 responses）
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`
- Modify: `example-all/.../controller/OrderController.java`
- Modify: `example-all/.../controller/OrderDbController.java`

**Interfaces:**
- Consumes: `TypeSchemaResolver.paramApiParam(...)` / `responses(Method)`（Task 4）、`ApiInfo.setResponses(...)`（Task 3）。
- Produces: `ParamInfo.required/description/example` 来源改为 `@ApiParam`（回退绑定注解 required）；`ApiInfo.responses` 被填充。

- [ ] **Step 1: 改 `SampleControllers`——参数迁移到 `@ApiParam` + 加方法级/响应演示**

将 import 中 `ApiField` 改为：

```java
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
```

`getUser` 方法改为（参数级 `@ApiParam` 取代 `@ApiField`，并加一条方法级 `@ApiParam` 与 `@ApiResponse`）：

```java
        @ApiDoc(summary = "查询用户", description = "按 ID 查询")
        @ApiParam(name = "id", required = true, value = "用户 ID")
        @ApiResponse(code = 200, description = "命中用户")
        @ApiResponse(code = 404, description = "用户不存在")
        @GetMapping("/{id}")
        public String getUser(@PathVariable Long id,
                @ApiParam(value = "过滤条件", example = "active") @RequestParam(required = false) String filter) {
            return "";
        }
```

- [ ] **Step 2: 改 `AbstractRestApiScanner.toParamInfo`——按 `@ApiParam` 规则取值**

补 import：

```java
import io.github.nianliu.archimedes.annotation.ApiParam;
```

将 `toParamInfo(MethodParameter parameter)` 整体替换为下述实现（先定源与解析名与绑定必填，再套 `@ApiParam`，最后组装）：

```java
    /**
     * 单参数 → ParamInfo：先按绑定注解定来源/解析名/绑定必填，
     * 再套自有 @ApiParam（参数级优先，方法级按 name 匹配）决定说明/示例/必填——
     * 命中 @ApiParam 用其 required，未命中回退绑定注解 required；说明/示例命中时取 @ApiParam，否则空串。
     */
    private ParamInfo toParamInfo(MethodParameter parameter) {
        String type = parameter.getGenericParameterType().getTypeName();
        java.util.Map<String, Object> validation = TypeSchemaResolver.paramValidation(parameter.getParameterAnnotations());

        // 1) 定来源、解析名、绑定注解必填
        ParamSource source;
        String name;
        boolean bindingRequired;
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (requestParam != null) {
            source = ParamSource.QUERY;
            name = firstNonEmpty(requestParam.name(), requestParam.value(), fallbackName(parameter));
            bindingRequired = requestParam.required();
        } else if (pathVariable != null) {
            source = ParamSource.PATH;
            name = firstNonEmpty(pathVariable.name(), pathVariable.value(), fallbackName(parameter));
            bindingRequired = pathVariable.required();
        } else if (requestHeader != null) {
            source = ParamSource.HEADER;
            name = firstNonEmpty(requestHeader.name(), requestHeader.value(), fallbackName(parameter));
            bindingRequired = requestHeader.required();
        } else if (requestBody != null) {
            source = ParamSource.BODY;
            name = fallbackName(parameter);
            bindingRequired = requestBody.required();
        } else {
            source = ParamSource.OTHER;
            name = fallbackName(parameter);
            bindingRequired = false;
        }

        // 2) 套 @ApiParam：命中则说明/示例/必填取注解，未命中必填回退绑定注解、说明/示例空串
        ApiParam apiParam = TypeSchemaResolver.paramApiParam(
                parameter.getMethod(), parameter.getParameterAnnotations(), name);
        String description = apiParam != null ? apiParam.value() : "";
        String example = apiParam != null ? apiParam.example() : "";
        boolean required = apiParam != null ? apiParam.required() : bindingRequired;

        // 3) 组装
        ParamInfo pi = new ParamInfo(name, source, type, required, description);
        pi.setValidation(validation);
        pi.setExample(example);
        return pi;
    }
```

> 说明：`MethodParameter.getMethod()` 对 handler 方法非 null（构造器场景才为 null，REST 参数不会命中）。

- [ ] **Step 3: `buildApiInfo` 填充 responses**

在 `buildApiInfo` 中 `info.setTagDescription(...)` 之后、`return info;` 之前追加：

```java
        // 响应契约：读取自有 @ApiResponse（按状态码分条）
        info.setResponses(TypeSchemaResolver.responses(method));
```

- [ ] **Step 4: watch signature 纳入 responses（保持契约变更检测完整）**

在 `ArchimedesWatchController.signature(...)` 的 REST 循环内，`appendField(sb.append("|res="), a.getResponseSchema());` 之后追加：

```java
            for (io.github.nianliu.archimedes.model.ResponseInfo r : a.getResponses()) {
                sb.append("|RS").append(r.getCode()).append(':').append(r.getDescription())
                        .append(':').append(r.getType());
                appendField(sb.append(':'), r.getSchema());
            }
```

- [ ] **Step 5: 更新/新增 `RestApiScannerTest` 断言**

`paramCarriesDescriptionAndExample` 保持不变（`filter` 的说明/示例现由参数级 `@ApiParam` 提供，值不变）。新增：

```java
    @Test
    void requiredFromApiParamAndResponses() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        ApiInfo getUser = find(apis, "/api/users/{id}");

        // 方法级 @ApiParam(name="id", required=true) 命中路径变量 id
        ParamInfo id = getUser.getParams().stream()
                .filter(p -> p.getName().equals("id")).findFirst().orElseThrow();
        assertThat(id.isRequired()).isTrue();

        // @ApiResponse 两条
        assertThat(getUser.getResponses()).extracting(r -> r.getCode())
                .containsExactlyInAnyOrder(200, 404);
    }

    @Test
    void requiredFallsBackToBindingWhenNoApiParam() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        // create 的 @RequestBody 无 @ApiParam → 必填回退绑定注解（@RequestBody 默认 required=true）
        ParamInfo body = find(apis, "/api/users").getParams().get(0);
        assertThat(body.isRequired()).isTrue();
    }
```

- [ ] **Step 6: 迁移 example-all `OrderController`**

import 中把 `ApiField` 换为 `ApiParam`、`ApiResponse`（保留 `ApiDoc`/`ApiModule`）：

```java
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
```

参数上的 `@ApiField(...)` 全部改为 `@ApiParam(...)`（字段名一致，直接替换注解名即可）。示例——`list`：

```java
    @ApiDoc(summary = "查询订单列表", description = "支持按状态过滤与分页，缺省返回全部订单")
    @GetMapping
    public List<OrderResponse> list(
            @ApiParam(value = "按订单状态过滤，缺省返回全部", example = "PAID")
            @RequestParam(required = false) String status,
            @ApiParam(value = "分页大小，默认 10", example = "20")
            @RequestParam(defaultValue = "10") int size) {
```

`detail`（加 `@ApiResponse` 演示 200/404）：

```java
    @ApiDoc(summary = "查询订单详情", description = "按订单号返回单个订单，演示 ResponseEntity 包装解包")
    @ApiResponse(code = 200, description = "命中订单", type = OrderResponse.class)
    @ApiResponse(code = 404, description = "订单不存在")
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> detail(
            @ApiParam(value = "订单号，形如 O-1001", example = "O-1001")
            @PathVariable String orderNo) {
```

`create` 的 `@RequestHeader` 参数注解由 `@ApiField` 改 `@ApiParam`：

```java
            @ApiParam(value = "幂等键，防止重复下单", example = "idem-20260708-001")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
```

> `OrderResponse`/`CreateOrderRequest`/`OrderItemPayload` POJO 字段上的 `@ApiField` **保持不变**。

- [ ] **Step 7: 迁移 example-all `OrderDbController`**

import 把 `ApiField` 换为 `ApiParam`；`getOrder`/`createOrder` 参数上的 `@ApiField(...)` 改为 `@ApiParam(...)`（字段名一致）。例如 `createOrder`：

```java
    @ApiDoc(summary = "写入订单", description = "MERGE 写入，演示更新类 SQL 的影响行数统计")
    @PostMapping("/api/db/orders")
    public Map<String, Object> createOrder(
            @ApiParam(value = "订单主键 ID", example = "100")
            @RequestParam int id,
            @ApiParam(value = "商品名称", example = "键盘")
            @RequestParam String item,
            @ApiParam(value = "订单金额", example = "199.00")
            @RequestParam double amount) {
```

- [ ] **Step 8: 全量构建 + 测试**

Run: `mvn -q -DskipTests install && mvn test 2>&1 | grep -E "BUILD|Tests run: [0-9]+, Failures: [1-9]|Errors: [1-9]"`
Expected: `BUILD SUCCESS`，无非零 Failures/Errors

- [ ] **Step 9: 提交**

```bash
git add -A
git commit -m "feat: REST 参数描述/必填切换为 @ApiParam，填充 @ApiResponse；example-all 迁移演示"
```

---

### Task 6: `@ApiField` 收窄为 `FIELD` 目标

> 此刻所有参数上的 `@ApiField` 均已迁移到 `@ApiParam`（Task 5），可安全移除 `PARAMETER` 目标。

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiField.java`

- [ ] **Step 1: 移除 `PARAMETER` 目标并更新 javadoc**

```java
/**
 * 标注在 POJO 字段上，描述该字段（请求体/响应体结构树的说明与必填）。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiField {

    /** 字段说明。 */
    String value() default "";

    /** 是否必填。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
```

- [ ] **Step 2: 全量构建验证无 `@ApiField`-on-参数 残留**

Run: `mvn -q -DskipTests install && mvn -q test 2>&1 | grep -E "BUILD|cannot|错误|Failures: [1-9]|Errors: [1-9]"`
Expected: `BUILD SUCCESS`（若有残留会编译报错 "annotation type not applicable"）

- [ ] **Step 3: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiField.java
git commit -m "refactor: @ApiField 收窄为仅 FIELD 目标（参数描述归 @ApiParam）"
```

---

### Task 7: 前端渲染「响应」区块

**Files:**
- Modify: `archimedes-core/src/main/resources/archimedes-ui/index.html`

**Interfaces:**
- Consumes: `/apis` 每个 REST 项的 `responses: [{code, description, type, schema}]`。

- [ ] **Step 1: 新增 `responsesHtml(api)` 渲染函数**

在 `schemaTableHtml` 函数之后插入：

```javascript
    /* 声明的响应（@ApiResponse）：状态码 + 说明 +（有 schema 时）字段表；无声明返回空串。 */
    function responsesHtml(api) {
        if (!api.responses || !api.responses.length) { return ''; }
        var rows = api.responses.map(function (r) {
            var typeLabel = r.type ? ' <span class="count">' + esc(r.type) + '</span>' : '';
            var table = r.schema ? schemaTableHtml('', r.schema) : '';
            return '<div class="resp-row"><span class="verb verb-' +
                (r.code < 300 ? 'GET' : 'DELETE') + '">' + esc(String(r.code)) + '</span> '
                + '<span>' + esc(r.description || '') + '</span>' + typeLabel + '</div>' + table;
        }).join('');
        return '<h3>Responses</h3>' + rows;
    }
```

- [ ] **Step 2: 在 `tryFormHtml` 挂载响应区块**

将 `tryFormHtml` 返回串中的 `+ schemaTableHtml('Response Fields', api.responseSchema)` 一行改为：

```javascript
            + schemaTableHtml('Response Fields', api.responseSchema)
            + responsesHtml(api)
```

- [ ] **Step 3: 加一条最小样式（可选，复用既有 verb chip 即可）**

在 `<style>` 内 `.api-desc` 规则附近加：

```css
        .resp-row { display:flex; align-items:center; gap:8px; margin:6px 0; }
```

- [ ] **Step 4: 构建 core 使资源打进 jar**

Run: `mvn -q -pl archimedes-core install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/resources/archimedes-ui/index.html
git commit -m "feat: 前端 REST 卡片新增 @ApiResponse 响应区块渲染"
```

---

### Task 8: 双端 e2e + 文档 + 真机验证

**Files:**
- Modify: `example-all/src/test/java/io/github/nianliu/archimedes/exampleall/AllFeaturesEndToEndTest.java`
- Modify: `docs/功能清单与任务列表.md`

**Interfaces:**
- Consumes: 全链路成品。

- [ ] **Step 1: 给 example-all e2e 加 responses 断言**

在 `AllFeaturesEndToEndTest` 中，`detail` 端点断言处（`/api/orders/{orderNo}` 对应项）新增（沿用文件内已有的取 apis map 的写法，`createOrder`/`params` 附近参照）：

```java
    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void detailDeclaresResponses() {
        Map<String, Object> apis = restForApis(); // 沿用本类既有获取 /apis 的辅助（若无则内联 rest.getForObject("/archimedes/apis", Map.class)）
        List<Map<String, Object>> rest = (List<Map<String, Object>>) apis.get("restApis");
        Map<String, Object> detail = rest.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/orders/{orderNo}"))
                .findFirst().orElseThrow();
        List<Map<String, Object>> responses = (List<Map<String, Object>>) detail.get("responses");
        assertThat(responses).extracting(r -> r.get("code")).contains(200, 404);
    }
```

> 若本类已有获取 `/apis` 的私有辅助方法，复用之替换 `restForApis()`；否则内联 `rest.getForObject("/archimedes/apis", Map.class)`（`rest` 为类中既有的 `TestRestTemplate` 字段）。

- [ ] **Step 2: 运行 example-all e2e**

Run: `mvn -q -pl example-all test -Dtest=AllFeaturesEndToEndTest`
Expected: PASS

- [ ] **Step 3: 更新功能清单文档**

在 `docs/功能清单与任务列表.md` 第 38 行「自有描述注解」条目与末尾「描述来源」锁定决策中，补充：`@ApiParam`(参数说明+必填,可标方法/参数,可重复)/`@ApiParams`(容器)/`@ApiResponse`(按状态码描述响应,可重复)/`@ApiResponses`(容器) 新增；`@ApiField` 收窄为仅 POJO 字段；参数必填来源改为 `@ApiParam`(未标注回退绑定注解)。（保持行文风格，追加一句即可。）

- [ ] **Step 4: 全量构建 + 测试**

Run: `mvn test 2>&1 | grep -E "archimedes-.*(SUCCESS|FAILURE)|BUILD SUCCESS|BUILD FAILURE"`
Expected: 各模块 SUCCESS，BUILD SUCCESS

- [ ] **Step 5: 真机验证**

```bash
netstat -ano | grep ':8082' | grep LISTENING | awk '{print $NF}' | while read p; do taskkill //PID $p //F; done
cd /d/Archimedes/example-all && nohup java -jar target/example-all-1.1-SNAPSHOT.jar > /tmp/exall.log 2>&1 &
```

等待就绪后校验 `detail` 端点参数必填与响应：

```bash
curl -s http://localhost:8082/archimedes/apis | python -c "
import json,sys
d=json.load(sys.stdin)
for a in d['restApis']:
    if '/api/orders/{orderNo}' in a.get('paths',[]):
        print('params:', [(p['name'],p['required'],p['description']) for p in a['params']])
        print('responses:', [(r['code'],r['description'],r.get('type')) for r in a['responses']])
"
```

Expected: `orderNo` 带说明；`responses` 含 (200,...) 与 (404,...)。随后 `taskkill` 停应用。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "test: example-all @ApiResponse e2e 断言；docs: 更新注解体系功能清单"
```

---

## Self-Review 结论

- **Spec 覆盖**：注解定义(T1/T2/T6) · 绑定与取值规则(T4/T5) · 模型 ResponseInfo+responses(T3) · 扫描解析(T4/T5) · 前端(T7) · 迁移与测试(T5/T8) · 文档(T8) · 兼容性破坏点 @ApiField 收窄(T6) —— 全部有对应任务。
- **类型一致**：`paramApiParam(Method, Annotation[], String)` / `responses(Method): List<ResponseInfo>` / `ResponseInfo(int,String,String,FieldInfo)` / `ApiInfo.get/setResponses` 在各任务间签名一致。
- **无占位符**：每个代码步骤给出完整代码；example-all 迁移为「注解名替换」的机械改动，样例已示。
- **绿色边界**：T1–T4 各自独立绿；T5 为参数源原子切换（core+example-all 同改）保持全量绿；T6 收窄前置于全部迁移之后。
