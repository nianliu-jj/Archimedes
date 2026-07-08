# Slice A: 自有接口描述注解体系 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `@ApiModule`/`@ApiDoc`/`@ApiField` 三个自有描述注解，取代 Swagger/Jackson 描述提取，并覆盖 REST + RPC + WebSocket 契约扫描。

**Architecture:** 注解置于 `archimedes-core` 的新包 `io.github.nianliu.archimedes.annotation`（RUNTIME 保留）。描述提取的唯一改动点是 `TypeSchemaResolver`——把其中读 Swagger/Jackson 描述的 FQCN 常量与逻辑切换为读自有注解（自有注解在 core，可直接引用类型，不再需要 FQCN 字符串反射）。RPC/WS 模型各加 `description` 字段，对应扫描器自省时读注解填充。validation 校验规则提取线（`@Pattern/@Size/...`）完全不动。

**Tech Stack:** Java 8 字节码（core `--release 8`，测试 `--release 17`）、JUnit 5、AssertJ、Spring MVC 测试脚手架、Maven 多模块。

## Global Constraints

- `archimedes-core` 主代码编译 `--release 8`，禁止 `List.of`/`Map.of`/`var`/`String.isBlank` 等 9+ API；测试代码 `--release 17` 可用。
- `archimedes-core` 零 servlet import（`javax.servlet.*` / `jakarta.servlet.*` 一律不得出现在 core）。
- 所有新 Java 文件类头须带 javadoc，含 `@author nianliu-jj` 与 `@since 2026-07-08`。
- 关键代码加中文注释；关键节点用 slf4j 记录（本 slice 以纯反射读取为主，日志仅在防御式降级处按需）。
- required 只认 `@ApiField#required`；validation 注解（`@NotNull` 等）**不再**作为 required 来源，但其校验规则提取（`extractValidation`）保持不变。
- 描述提取只认自有注解，移除对 Swagger v3/v2、Jackson 描述注解的读取。
- 每完成一个 task 用中文 commit message 提交。
- 走 OpenSpec 流程：本 plan 对应 change `api-annotations`（propose 已在 brainstorming 外围完成设计，apply 阶段逐 task 实现，结束 archive）。

---

## Task 1: 三个描述注解

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiModule.java`
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiDoc.java`
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ApiField.java`
- Test: `archimedes-core/src/test/java/io/github/nianliu/archimedes/annotation/AnnotationContractTest.java`

**Interfaces:**
- Produces:
  - `@ApiModule` — `@Target(TYPE) @Retention(RUNTIME)`；`String value() default ""`、`String name() default ""`、`String description() default ""`
  - `@ApiDoc` — `@Target(METHOD) @Retention(RUNTIME)`；`String summary() default ""`、`String value() default ""`、`String description() default ""`、`boolean deprecated() default false`
  - `@ApiField` — `@Target({PARAMETER, FIELD}) @Retention(RUNTIME)`；`String value() default ""`、`boolean required() default false`、`String example() default ""`
  - 别名取值约定（供 Task 2 消费）：module 名 = `name` 非空则用 `name`，否则用 `value`；doc 摘要 = `summary` 非空则用 `summary`，否则用 `value`。

- [ ] **Step 1: 写失败测试**

`AnnotationContractTest.java`：
```java
package io.github.nianliu.archimedes.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationContractTest {

    @ApiModule(value = "订单", description = "订单管理")
    @ApiDoc(summary = "创建订单", description = "下单并返回单号", deprecated = true)
    static class Sample {
        @ApiField(value = "商品ID", required = true, example = "1001")
        private Long itemId;
    }

    @Test
    void apiModuleRetainedAtRuntimeWithAttributes() {
        ApiModule m = Sample.class.getAnnotation(ApiModule.class);
        assertThat(m).isNotNull();
        assertThat(m.value()).isEqualTo("订单");
        assertThat(m.name()).isEmpty();
        assertThat(m.description()).isEqualTo("订单管理");
        assertThat(ApiModule.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Arrays.asList(ApiModule.class.getAnnotation(Target.class).value()))
                .containsExactly(ElementType.TYPE);
    }

    @Test
    void apiDocRetainedAtRuntimeWithAttributes() {
        ApiDoc d = Sample.class.getAnnotation(ApiDoc.class);
        assertThat(d.summary()).isEqualTo("创建订单");
        assertThat(d.description()).isEqualTo("下单并返回单号");
        assertThat(d.deprecated()).isTrue();
        assertThat(Arrays.asList(ApiDoc.class.getAnnotation(Target.class).value()))
                .containsExactly(ElementType.METHOD);
    }

    @Test
    void apiFieldTargetsParameterAndField() throws Exception {
        ApiField f = Sample.class.getDeclaredField("itemId").getAnnotation(ApiField.class);
        assertThat(f.value()).isEqualTo("商品ID");
        assertThat(f.required()).isTrue();
        assertThat(f.example()).isEqualTo("1001");
        assertThat(Arrays.asList(ApiField.class.getAnnotation(Target.class).value()))
                .containsExactlyInAnyOrder(ElementType.PARAMETER, ElementType.FIELD);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=AnnotationContractTest`
Expected: 编译失败（`ApiModule` 等符号不存在）。

- [ ] **Step 3: 写三个注解**

`ApiModule.java`：
```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller / RPC 服务接口类上，描述其所属模块（分组）。
 * <p>{@link #name()} 为空时回退 {@link #value()}，供前端按模块聚合展示。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiModule {

    /** 模块名（{@link #name()} 的别名，二选一）。 */
    String value() default "";

    /** 模块名（优先于 {@link #value()}）。 */
    String name() default "";

    /** 模块描述。 */
    String description() default "";
}
```

`ApiDoc.java`：
```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 REST handler / RPC 方法 / WebSocket handler 方法上，描述该接口。
 * <p>{@link #summary()} 为空时回退 {@link #value()}。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDoc {

    /** 接口摘要（{@link #value()} 的别名）。 */
    String summary() default "";

    /** 接口摘要（别名，与 {@link #summary()} 二选一）。 */
    String value() default "";

    /** 接口详细描述。 */
    String description() default "";

    /** 是否弃用（与 {@code @Deprecated} 取或）。 */
    boolean deprecated() default false;
}
```

`ApiField.java`：
```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在方法参数与 POJO 字段上，描述该参数/字段。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiField {

    /** 参数/字段说明。 */
    String value() default "";

    /** 是否必填。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=AnnotationContractTest`
Expected: PASS（3 tests）。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/ archimedes-core/src/test/java/io/github/nianliu/archimedes/annotation/
git commit -m "feat: 新增自有接口描述注解 @ApiModule/@ApiDoc/@ApiField"
```

---

## Task 2: TypeSchemaResolver 切换到自有注解

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolver.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolverTest.java`

**Interfaces:**
- Consumes: `@ApiModule`/`@ApiDoc`/`@ApiField`（Task 1）
- Produces（签名不变，仅实现改为读自有注解）：
  - `static String operationSummary(Annotation[] methodAnnotations)` — 读 `@ApiDoc`，summary 空回退 value；无则空串
  - `static String operationDescription(Annotation[] methodAnnotations)` — 读 `@ApiDoc#description`
  - `static boolean operationDeprecated(Annotation[] methodAnnotations)` — **新增**，读 `@ApiDoc#deprecated`
  - `static String tagName(Annotation[] classAnnotations, String fallbackClassName)` — 读 `@ApiModule`，name 空回退 value，再回退类简名去 Controller 后缀
  - `static String tagDescription(Annotation[] classAnnotations)` — 读 `@ApiModule#description`
  - `static String paramDescription(Annotation[] annotations)` — 读 `@ApiField#value`
  - `static String paramExample(Annotation[] annotations)` — **新增**，读 `@ApiField#example`；无则空串
  - `extractValidation` / `paramValidation` — 不变

**Interface note:** 描述提取改为直接引用 `ApiField.class` 等类型读取（core 内可直接依赖），不再走 `annotationString` 的 FQCN 字符串路径；`annotationString` 若仅剩 validation 用途可保留。字段说明/必填的内部方法 `fieldDescription`/`fieldRequired` 同步改为读 `@ApiField`。

- [ ] **Step 1: 改测试为自有注解断言**

把 `TypeSchemaResolverTest.java` 中 Swagger/Jackson 描述注解替换为自有注解（保留 Jackson `@JsonProperty`/`@JsonIgnore` 改名/剔除逻辑不变，那不是描述提取）：
```java
package io.github.nianliu.archimedes.scanner.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nianliu.archimedes.annotation.ApiField;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSchemaResolverTest {

    enum OrderStatus { CREATED, PAID }

    static class OrderItem {
        @ApiField(value = "商品 ID", required = true)
        private Long productId;
        private int quantity;
    }

    static class CreateOrderRequest {
        @ApiField(value = "订单标题", required = true)
        private String title;
        @JsonProperty("order_no")
        private String orderNo;
        @JsonIgnore
        private String internalToken;
        private OrderStatus status;
        private List<OrderItem> items;
        private Map<String, OrderItem> extras;
        @ApiField("下单备注")
        private String remark;
    }

    static class TreeNode {
        private String label;
        private List<TreeNode> children;
    }

    static class Holder {
        ResponseEntity<List<OrderItem>> wrappedList() { return null; }
        Mono<OrderItem> mono() { return null; }
        void nothing() { }
        String scalar() { return null; }
    }

    private static Type returnType(String method) throws Exception {
        return Holder.class.getDeclaredMethod(method).getGenericReturnType();
    }

    private static FieldInfo child(FieldInfo node, String name) {
        return node.getChildren().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Test
    void resolvesNestedPojoWithAnnotations() {
        FieldInfo root = TypeSchemaResolver.resolve(CreateOrderRequest.class);

        assertThat(root).isNotNull();
        assertThat(root.getType()).isEqualTo("CreateOrderRequest");
        assertThat(root.isArray()).isFalse();

        FieldInfo title = child(root, "title");
        assertThat(title.getDescription()).isEqualTo("订单标题");
        assertThat(title.isRequired()).isTrue();
        assertThat(title.getType()).isEqualTo("String");

        assertThat(child(root, "order_no").getType()).isEqualTo("String");
        assertThat(root.getChildren()).noneMatch(c -> c.getName().equals("internalToken"));

        FieldInfo status = child(root, "status");
        assertThat(status.getType()).isEqualTo("OrderStatus");
        assertThat(status.getDescription()).contains("CREATED / PAID");

        FieldInfo items = child(root, "items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.getType()).isEqualTo("OrderItem");
        FieldInfo productId = child(items, "productId");
        assertThat(productId.getDescription()).isEqualTo("商品 ID");
        assertThat(productId.isRequired()).isTrue();
        assertThat(child(items, "quantity").getType()).isEqualTo("int");

        FieldInfo extras = child(root, "extras");
        assertThat(extras.getType()).isEqualTo("Map<String, OrderItem>");
        assertThat(extras.getChildren()).isNotEmpty();

        assertThat(child(root, "remark").getDescription()).isEqualTo("下单备注");
    }

    @Test
    void unwrapsCommonWrappers() throws Exception {
        FieldInfo wrapped = TypeSchemaResolver.resolve(returnType("wrappedList"));
        assertThat(wrapped.isArray()).isTrue();
        assertThat(wrapped.getType()).isEqualTo("OrderItem");
        assertThat(wrapped.getChildren()).extracting(FieldInfo::getName)
                .containsExactlyInAnyOrder("productId", "quantity");

        FieldInfo mono = TypeSchemaResolver.resolve(returnType("mono"));
        assertThat(mono.isArray()).isFalse();
        assertThat(mono.getType()).isEqualTo("OrderItem");

        assertThat(TypeSchemaResolver.resolve(returnType("nothing"))).isNull();
        FieldInfo scalar = TypeSchemaResolver.resolve(returnType("scalar"));
        assertThat(scalar.getType()).isEqualTo("String");
        assertThat(scalar.getChildren()).isEmpty();
    }

    @Test
    void guardsAgainstSelfReference() {
        FieldInfo root = TypeSchemaResolver.resolve(TreeNode.class);
        FieldInfo children = child(root, "children");
        assertThat(children.isArray()).isTrue();
        assertThat(children.getType()).isEqualTo("TreeNode");
        assertThat(children.getDescription()).contains("递归引用");
        assertThat(children.getChildren()).isEmpty();
    }
}
```

新增一个提取门面测试 `SchemaFacadeTest.java`（同包）验证方法/类/参数级注解读取与别名回退、deprecated、example：
```java
package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiField;
import io.github.nianliu.archimedes.annotation.ApiModule;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFacadeTest {

    @ApiModule("订单")
    static class ValueAliasModule { }

    @ApiModule(name = "订单管理", description = "订单域")
    static class NamedModule { }

    static class Handlers {
        @ApiDoc(value = "别名摘要")
        void aliased() { }

        @ApiDoc(summary = "创建", description = "下单", deprecated = true)
        void full() { }

        void bare(@ApiField(value = "关键字", example = "kw") String q) { }
    }

    private static Annotation[] method(String name) throws Exception {
        return Handlers.class.getDeclaredMethod(name).getAnnotations();
    }

    @Test
    void moduleNameFallsBackToValue() {
        assertThat(TypeSchemaResolver.tagName(
                ValueAliasModule.class.getAnnotations(), ValueAliasModule.class.getName()))
                .isEqualTo("订单");
        assertThat(TypeSchemaResolver.tagName(
                NamedModule.class.getAnnotations(), NamedModule.class.getName()))
                .isEqualTo("订单管理");
        assertThat(TypeSchemaResolver.tagDescription(NamedModule.class.getAnnotations()))
                .isEqualTo("订单域");
    }

    @Test
    void moduleNameFallsBackToClassSimpleNameWithoutAnnotation() {
        assertThat(TypeSchemaResolver.tagName(new Annotation[0],
                "com.demo.OrderController")).isEqualTo("Order");
    }

    @Test
    void docSummaryFallsBackToValueAndReadsDeprecated() throws Exception {
        assertThat(TypeSchemaResolver.operationSummary(method("aliased"))).isEqualTo("别名摘要");
        assertThat(TypeSchemaResolver.operationSummary(method("full"))).isEqualTo("创建");
        assertThat(TypeSchemaResolver.operationDescription(method("full"))).isEqualTo("下单");
        assertThat(TypeSchemaResolver.operationDeprecated(method("full"))).isTrue();
        assertThat(TypeSchemaResolver.operationDeprecated(method("aliased"))).isFalse();
    }

    @Test
    void paramDescriptionAndExample() throws Exception {
        Annotation[] paramAnns = Handlers.class
                .getDeclaredMethod("bare", String.class)
                .getParameterAnnotations()[0];
        assertThat(TypeSchemaResolver.paramDescription(paramAnns)).isEqualTo("关键字");
        assertThat(TypeSchemaResolver.paramExample(paramAnns)).isEqualTo("kw");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=TypeSchemaResolverTest,SchemaFacadeTest`
Expected: 编译失败（`operationDeprecated`/`paramExample` 不存在）或断言失败（仍读 Swagger）。

- [ ] **Step 3: 改 TypeSchemaResolver 实现**

在 `TypeSchemaResolver` 顶部 import 三注解：
```java
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiField;
import io.github.nianliu.archimedes.annotation.ApiModule;
```

删除 Swagger/Jackson 描述相关的 FQCN 常量数组（`DESCRIPTION_ANNOTATIONS`、`PARAM_DESCRIPTION_ANNOTATIONS`）与 `REQUIRED_ANNOTATIONS`（validation required 来源，本 slice 取消——required 只认 `@ApiField`）。保留 `extractValidation`/`paramValidation` 及其内部逻辑。

替换以下方法体（直接类型读取，防御式 try/catch 保持"任何异常降级空/false"）：
```java
/** 从方法参数注解中提取参数说明（@ApiField#value），无则空串。 */
public static String paramDescription(Annotation[] annotations) {
    ApiField f = find(annotations, ApiField.class);
    return f != null ? f.value() : "";
}

/** 从方法参数注解中提取示例值（@ApiField#example），无则空串。 */
public static String paramExample(Annotation[] annotations) {
    ApiField f = find(annotations, ApiField.class);
    return f != null ? f.example() : "";
}

/** 接口摘要：@ApiDoc#summary 空则回退 value；无注解空串。 */
public static String operationSummary(Annotation[] methodAnnotations) {
    ApiDoc d = find(methodAnnotations, ApiDoc.class);
    if (d == null) {
        return "";
    }
    return !d.summary().isEmpty() ? d.summary() : d.value();
}

/** 接口描述：@ApiDoc#description，无则空串。 */
public static String operationDescription(Annotation[] methodAnnotations) {
    ApiDoc d = find(methodAnnotations, ApiDoc.class);
    return d != null ? d.description() : "";
}

/** 接口弃用标记：@ApiDoc#deprecated，无则 false。 */
public static boolean operationDeprecated(Annotation[] methodAnnotations) {
    ApiDoc d = find(methodAnnotations, ApiDoc.class);
    return d != null && d.deprecated();
}

/** 模块名：@ApiModule name 空回退 value；无注解回退类简名去 Controller 后缀。 */
public static String tagName(Annotation[] classAnnotations, String fallbackClassName) {
    ApiModule m = find(classAnnotations, ApiModule.class);
    if (m != null) {
        String name = !m.name().isEmpty() ? m.name() : m.value();
        if (!name.isEmpty()) {
            return name;
        }
    }
    String simple = fallbackClassName.contains(".")
            ? fallbackClassName.substring(fallbackClassName.lastIndexOf('.') + 1) : fallbackClassName;
    return simple.endsWith("Controller") ? simple.substring(0, simple.length() - 10) : simple;
}

/** 模块描述：@ApiModule#description，无则空串。 */
public static String tagDescription(Annotation[] classAnnotations) {
    ApiModule m = find(classAnnotations, ApiModule.class);
    return m != null ? m.description() : "";
}
```

内部字段说明/必填改读 `@ApiField`：
```java
private static String fieldDescription(Field field) {
    ApiField f = field.getAnnotation(ApiField.class);
    return f != null ? f.value() : "";
}

private static boolean fieldRequired(Field field) {
    ApiField f = field.getAnnotation(ApiField.class);
    return f != null && f.required();
}
```

新增查找工具（放类内私有静态区）：
```java
/** 在注解数组中按类型查找注解实例，无则 null。 */
private static <A extends Annotation> A find(Annotation[] annotations, Class<A> type) {
    if (annotations == null) {
        return null;
    }
    for (Annotation a : annotations) {
        if (type.isInstance(a)) {
            return type.cast(a);
        }
    }
    return null;
}
```

若删除描述常量后仍有方法引用 `annotationString`（`resolveFields` 用它读 `@JsonProperty#value` 改名）与 `hasAnnotation`（读 `@JsonIgnore` 剔除字段）——**这两个方法必须保留**，它们是结构处理不是描述提取。仅删除已无引用的 `DESCRIPTION_ANNOTATIONS`/`PARAM_DESCRIPTION_ANNOTATIONS`/`REQUIRED_ANNOTATIONS` 常量。`extractValidation` 内直接用 `a.annotationType().getMethod(...)` 反射，不依赖 `annotationString`，保持不变。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=TypeSchemaResolverTest,SchemaFacadeTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolver.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/
git commit -m "refactor: TypeSchemaResolver 描述提取切换为自有注解，移除 Swagger/Jackson 描述读取"
```

---

## Task 3: REST 扫描器接入 @ApiDoc#deprecated

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java:96-99`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`

**Interfaces:**
- Consumes: `TypeSchemaResolver.operationDeprecated`（Task 2）、`@ApiModule`/`@ApiDoc`（Task 1）
- Produces: 无新 API（行为增强：deprecated 取 `@Deprecated` 或 `@ApiDoc#deprecated`）

- [ ] **Step 1: 改测试**

`SampleControllers.java` 的 `UserController` 加类级 `@ApiModule` 与一个用 `@ApiDoc(deprecated=true)`（而非 `@Deprecated`）标注的方法：
```java
// import 增加
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;

@RestController
@RequestMapping("/api/users")
@ApiModule(name = "用户", description = "用户管理")
public static class UserController {

    @ApiDoc(summary = "查询用户", description = "按 ID 查询")
    @GetMapping("/{id}")
    public String getUser(@PathVariable Long id, @RequestParam(required = false) String filter) {
        return "";
    }

    @PostMapping
    public String create(@RequestBody String body) {
        return "";
    }

    @Deprecated
    @GetMapping("/legacy")
    public List<String> legacy() {
        return List.of();
    }

    @ApiDoc(summary = "试验接口", deprecated = true)
    @GetMapping("/beta")
    public String beta() {
        return "";
    }
}
```

`RestApiScannerTest.java` 增测试：
```java
@Test
void readsModuleAndDocFromOwnAnnotations() {
    List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

    ApiInfo getUser = find(apis, "/api/users/{id}");
    assertThat(getUser.getSummary()).isEqualTo("查询用户");
    assertThat(getUser.getOperationDescription()).isEqualTo("按 ID 查询");
    assertThat(getUser.getTag()).isEqualTo("用户");
    assertThat(getUser.getTagDescription()).isEqualTo("用户管理");
}

@Test
void deprecatedFromApiDocFlag() {
    List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
    assertThat(find(apis, "/api/users/beta").isDeprecated()).isTrue();
    // 传统 @Deprecated 仍生效
    assertThat(find(apis, "/api/users/legacy").isDeprecated()).isTrue();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=RestApiScannerTest`
Expected: `deprecatedFromApiDocFlag` FAIL（beta 未被识别为 deprecated）。

- [ ] **Step 3: 改 AbstractRestApiScanner**

`buildApiInfo` 中 deprecated 一行改为并入 `@ApiDoc#deprecated`：
```java
info.setDeprecated(method.isAnnotationPresent(Deprecated.class)
        || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class)
        || TypeSchemaResolver.operationDeprecated(method.getAnnotations()));
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=RestApiScannerTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/
git commit -m "feat: REST 扫描器 deprecated 并入 @ApiDoc#deprecated"
```

---

## Task 4: RPC/WS 模型新增 description 字段

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/RpcApiInfo.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/RpcMethodInfo.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/WsApiInfo.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java`

**Interfaces:**
- Produces:
  - `RpcApiInfo#getDescription()` / `setDescription(String)`（默认 null）
  - `RpcMethodInfo#getDescription()` / `setDescription(String)`（默认 null）
  - `WsApiInfo#getDescription()` / `setDescription(String)`（默认 null）
  - 三者现有构造器签名保持不变（description 只经 setter 设置，序列化恒含该字段）

- [ ] **Step 1: 写失败测试**

在 `ApiModelTest.java` 增：
```java
@Test
void rpcAndWsCarryDescription() {
    RpcApiInfo svc = new RpcApiInfo(RpcApiInfo.PROTOCOL_DUBBO, "com.demo.S", null, null, null);
    svc.setDescription("定价服务");
    assertThat(svc.getDescription()).isEqualTo("定价服务");

    RpcMethodInfo m = new RpcMethodInfo("price", null, "java.math.BigDecimal");
    m.setDescription("计算价格");
    assertThat(m.getDescription()).isEqualTo("计算价格");

    WsApiInfo ws = new WsApiInfo(WsApiInfo.KIND_HANDLER, "/ws/echo", "EchoHandler", null, false);
    ws.setDescription("回声端点");
    assertThat(ws.getDescription()).isEqualTo("回声端点");
}
```
（若 `ApiModelTest` 无 import，补 `io.github.nianliu.archimedes.model.*` 相关 import 与 `import static org.assertj.core.api.Assertions.assertThat;`。）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=ApiModelTest`
Expected: 编译失败（`setDescription` 不存在）。

- [ ] **Step 3: 三个模型各加字段与 getter/setter**

`RpcApiInfo.java` 加：
```java
/** 服务级描述（读服务接口类的 @ApiModule#description；可为 null）。 */
private String description;

public String getDescription() {
    return description;
}

/** 设置服务级描述。 */
public void setDescription(String description) {
    this.description = description;
}
```

`RpcMethodInfo.java` 加：
```java
/** 方法级描述（读方法上的 @ApiDoc；可为 null）。 */
private String description;

public String getDescription() {
    return description;
}

/** 设置方法级描述。 */
public void setDescription(String description) {
    this.description = description;
}
```

`WsApiInfo.java` 加：
```java
/** 端点描述（读 handler 类/方法上的 @ApiDoc；可为 null）。 */
private String description;

public String getDescription() {
    return description;
}

/** 设置端点描述。 */
public void setDescription(String description) {
    this.description = description;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=ApiModelTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ archimedes-core/src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java
git commit -m "feat: RpcApiInfo/RpcMethodInfo/WsApiInfo 新增 description 字段"
```

---

## Task 5: Dubbo/gRPC 扫描器读注解填充 description

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/DubboRpcScanner.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/GrpcRpcScanner.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/rpc/DubboRpcScannerTest.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/rpc/GrpcRpcScannerTest.java`

**Interfaces:**
- Consumes: `@ApiModule`/`@ApiDoc`（Task 1）、模型 description setter（Task 4）
- Produces: 服务/方法级 description 已填充（缺注解为 null）

- [ ] **Step 1: 写失败测试（Dubbo）**

先看现有 `DubboRpcScannerTest` 里的样例服务接口类名，在其接口上加 `@ApiModule` 与在某方法上加 `@ApiDoc`，断言：
```java
// 在样例接口类上加：
// @ApiModule(name = "定价", description = "定价服务")
// interface 方法上加：@ApiDoc(summary = "计算价格", description = "按数量计价")
@Test
void fillsServiceAndMethodDescriptionFromAnnotations() {
    RpcApiInfo svc = /* 定位到样例服务的扫描结果 */ ;
    assertThat(svc.getDescription()).isEqualTo("定价服务");
    RpcMethodInfo priced = svc.getMethods().stream()
            .filter(m -> m.getDescription() != null)
            .findFirst().orElseThrow();
    assertThat(priced.getDescription()).isEqualTo("按数量计价");
}
```
（实现者：按 `DubboRpcScannerTest` 现有样例接口补注解与断言；gRPC 同理，在其手写 BindableService 对应的服务接口/方法上加注解。若 gRPC 测试的服务无独立业务接口可标注，则在 `GrpcRpcScannerTest` 中新增一个带 `@ApiModule`/`@ApiDoc` 的接口用于断言 description 读取路径。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=DubboRpcScannerTest,GrpcRpcScannerTest`
Expected: FAIL（description 为 null）。

- [ ] **Step 3: 改扫描器**

`DubboRpcScanner.describe`：填充服务级与方法级 description。
```java
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;

// 构建 RpcApiInfo 后（return 前）：
RpcApiInfo api = new RpcApiInfo(RpcApiInfo.PROTOCOL_DUBBO,
        serviceBean.getInterface(), serviceBean.getVersion(), serviceBean.getGroup(), methods);
if (interfaceClass != null) {
    api.setDescription(TypeSchemaResolver.tagDescriptionOrNull(interfaceClass.getAnnotations()));
}
return api;
```
方法级：在 `methods.add(...)` 处改为构造后 setDescription：
```java
RpcMethodInfo mi = new RpcMethodInfo(method.getName(), parameterTypes, method.getReturnType().getName());
ApiDoc doc = method.getAnnotation(ApiDoc.class);
if (doc != null) {
    String text = !doc.description().isEmpty() ? doc.description()
            : (!doc.summary().isEmpty() ? doc.summary() : doc.value());
    mi.setDescription(text.isEmpty() ? null : text);
}
methods.add(mi);
```

为避免服务级 description 出现空串（模型语义 null=无），在 `TypeSchemaResolver` 加一个返回 null 的便捷方法（Task 2 的 `tagDescription` 返回空串，RPC 侧要 null）：
```java
/** 同 tagDescription，但无描述时返回 null（供 RPC/WS 模型 null 语义）。 */
public static String tagDescriptionOrNull(Annotation[] annotations) {
    String d = tagDescription(annotations);
    return d.isEmpty() ? null : d;
}
```

`GrpcRpcScanner`：同法在服务接口类/方法上读 `@ApiModule`/`@ApiDoc`（gRPC 以 BindableService 自省，服务描述读其实现类或绑定接口的注解，按现有扫描器取到的 `Class<?>` 来源填充）。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=DubboRpcScannerTest,GrpcRpcScannerTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/DubboRpcScanner.java archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/GrpcRpcScanner.java archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/TypeSchemaResolver.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/rpc/
git commit -m "feat: Dubbo/gRPC 扫描器读 @ApiModule/@ApiDoc 填充 description"
```

---

## Task 6: SOFA_TR/tRPC 反射扫描器读注解填充 description

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/AnnotatedRpcScannerSupport.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/SofaTrRpcScanner.java`（如描述填充落在此层）
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/TrpcRpcScanner.java`（同上）
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/rpc/AnnotatedRpcScannersTest.java`

**Interfaces:**
- Consumes: `@ApiModule`/`@ApiDoc`、`RpcApiInfo/RpcMethodInfo#setDescription`、`TypeSchemaResolver.tagDescriptionOrNull`
- Produces: SOFA_TR/tRPC 服务的 description 已填充

- [ ] **Step 1: 写失败测试**

阅读 `AnnotatedRpcScannersTest` 现有 stub 服务（同 FQCN SOFA/tRPC 注解驱动），在其被扫描的目标接口/实现类上加 `@ApiModule`/`@ApiDoc`，断言扫出的 `RpcApiInfo.getDescription()` 与某方法 `RpcMethodInfo.getDescription()` 非空且等于注解值。（实现者按现有测试结构补断言。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=AnnotatedRpcScannersTest`
Expected: FAIL。

- [ ] **Step 3: 改 AnnotatedRpcScannerSupport**

在 support 基类构建 `RpcApiInfo`/`RpcMethodInfo` 的公共位置，读目标类/方法的 `@ApiModule`/`@ApiDoc` 填充 description（与 Task 5 同一套读取逻辑；抽一个 `applyDescriptions(Class<?> serviceType, RpcApiInfo api)` 与方法级 helper 复用）。若 support 是 SOFA/tRPC 共享骨架，改这一处即可覆盖两协议。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=AnnotatedRpcScannersTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/rpc/ archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/rpc/AnnotatedRpcScannersTest.java
git commit -m "feat: SOFA_TR/tRPC 扫描器读 @ApiModule/@ApiDoc 填充 description"
```

---

## Task 7: WebSocket 扫描器读 @ApiDoc 填充 description

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/ws/SpringWebSocketHandlerScanner.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/ws/StompMappingScanner.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/ws/SpringWebSocketHandlerScannerTest.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/ws/StompMappingScannerTest.java`

**Interfaces:**
- Consumes: `@ApiDoc`、`WsApiInfo#setDescription`
- Produces: WS 端点 description 已填充（STOMP 方法级读方法 `@ApiDoc`，handler 类级读类 `@ApiDoc` 若适用；无则 null）

- [ ] **Step 1: 写失败测试**

`StompMappingScannerTest`：在样例 `@MessageMapping` 方法上加 `@ApiDoc(summary="发送消息")`，断言对应 `WsApiInfo.getDescription()` 等于 "发送消息"。
`SpringWebSocketHandlerScannerTest`：在样例 handler 类上加 `@ApiDoc(summary="回声")`（`@ApiDoc` 目标是 METHOD——handler 无业务方法可标，故 handler 类级描述改用 `@ApiModule#description`；测试断言读 `@ApiModule` 填充的 description）。

**Interface note:** `@ApiDoc` 仅 `@Target(METHOD)`，故 STOMP 方法级用 `@ApiDoc`；handler 类级用 `@ApiModule#description`（类目标）。扫描器据形态选注解来源。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=SpringWebSocketHandlerScannerTest,StompMappingScannerTest`
Expected: FAIL。

- [ ] **Step 3: 改 WS 扫描器**

`StompMappingScanner`：构建方法级 `WsApiInfo` 后读方法 `@ApiDoc`：
```java
import io.github.nianliu.archimedes.annotation.ApiDoc;
// ...
ApiDoc doc = method.getAnnotation(ApiDoc.class);
if (doc != null) {
    String text = !doc.summary().isEmpty() ? doc.summary()
            : (!doc.value().isEmpty() ? doc.value() : doc.description());
    info.setDescription(text.isEmpty() ? null : text);
}
```
`SpringWebSocketHandlerScanner`：handler 类级读 `@ApiModule#description`：
```java
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
// ...
info.setDescription(TypeSchemaResolver.tagDescriptionOrNull(handlerClass.getAnnotations()));
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=SpringWebSocketHandlerScannerTest,StompMappingScannerTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/ws/ archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/ws/
git commit -m "feat: WebSocket 扫描器读 @ApiDoc/@ApiModule 填充 description"
```

---

## Task 8: 参数 example 贯通到 ParamInfo（try-it 预填增强）

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ParamInfo.java`
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`

**Interfaces:**
- Consumes: `TypeSchemaResolver.paramExample`（Task 2）、`@ApiField`（Task 1）
- Produces: `ParamInfo#getExample()` / `setExample(String)`（默认 null）

- [ ] **Step 1: 写失败测试**

`SampleControllers.UserController.getUser` 的 `filter` 参数加 `@ApiField(value="过滤条件", example="active")`：
```java
import io.github.nianliu.archimedes.annotation.ApiField;
// ...
public String getUser(@PathVariable Long id,
        @ApiField(value = "过滤条件", example = "active") @RequestParam(required = false) String filter) {
```
`RestApiScannerTest` 增：
```java
@Test
void paramCarriesDescriptionAndExample() {
    List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
    ParamInfo filter = find(apis, "/api/users/{id}").getParams().stream()
            .filter(p -> p.getName().equals("filter")).findFirst().orElseThrow();
    assertThat(filter.getDescription()).isEqualTo("过滤条件");
    assertThat(filter.getExample()).isEqualTo("active");
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=RestApiScannerTest`
Expected: 编译失败（`getExample` 不存在）。

- [ ] **Step 3: 改 ParamInfo + 扫描器**

`ParamInfo.java` 加字段与 getter/setter：
```java
/** 示例值（@ApiField#example；供 UI 在线调试预填；可为 null）。 */
private String example;

public String getExample() {
    return example;
}

/** 设置示例值。 */
public void setExample(String example) {
    this.example = example;
}
```
`AbstractRestApiScanner.toParamInfo`：每个分支 setValidation 之后补 setExample。为避免重复，在方法末尾统一设置——重构为：先算 `example`，各分支 return 前 `pi.setExample(example)`。最简做法：在方法开头
```java
String example = TypeSchemaResolver.paramExample(parameter.getParameterAnnotations());
```
并把每个 `pi.setValidation(validation); return pi;` 改为 `pi.setValidation(validation); pi.setExample(example); return pi;`（含最后的 OTHER 分支）。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=RestApiScannerTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/model/ParamInfo.java archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java
git commit -m "feat: ParamInfo 新增 example，@ApiField#example 贯通到参数契约"
```

---

## Task 9: example-all 迁移到自有注解

**Files:**
- Modify: `example-all/pom.xml`（移除 `swagger-annotations` 依赖）
- Modify: `example-all/src/main/java/io/github/nianliu/archimedes/exampleall/**`（Controller/model/RPC/WS 用到 Swagger 注解处改自有注解）
- Modify: `example-all/src/test/java/io/github/nianliu/archimedes/exampleall/AllFeaturesEndToEndTest.java`（如断言过 Swagger 来源的描述，改断言自有注解值）

**Interfaces:**
- Consumes: 全部自有注解与扫描器接入（Task 1–8）

- [ ] **Step 1: 定位 Swagger 用法**

Run（列出 example-all 中所有 Swagger 注解与断言）:
`grep -rn "io.swagger" example-all/src`
逐文件把 `@Operation`→`@ApiDoc`、`@Tag`→`@ApiModule`、`@Parameter`/`@Schema`→`@ApiField` 迁移；保留 `jakarta.validation.*`（前端校验演示）。

- [ ] **Step 2: 移除依赖并改代码**

`example-all/pom.xml` 删除：
```xml
<dependency>
    <groupId>io.swagger.core.v3</groupId>
    <artifactId>swagger-annotations</artifactId>
    <version>${swagger-annotations.version}</version>
</dependency>
```
以及 `<swagger-annotations.version>` property（若无其它引用）。代码按 Step 1 迁移。

- [ ] **Step 3: 运行 example-all 测试**

Run: `mvn -q -pl example-all -am test`
Expected: PASS（如 e2e 断言过描述值，确保与迁移后的自有注解值一致）。

- [ ] **Step 4: 提交**

```bash
git add example-all/
git commit -m "refactor: example-all 迁移到自有注解，移除 swagger-annotations 依赖"
```

---

## Task 10: 全量回归 + 文档 + OpenSpec 归档

**Files:**
- Modify: `README.md`（描述来源改为自有注解，新增注解用法小节）
- Modify: `docs/功能清单与任务列表.md`（S15/契约增强条目补注自有注解；锁定决策表补记"描述只认自有注解"）
- Create/Modify: OpenSpec change `api-annotations` 工件（若走完整 opsx 流程）

**Interfaces:**
- Consumes: 全部前序 Task

- [ ] **Step 1: 全量测试**

Run: `mvn test`
Expected: BUILD SUCCESS，全模块全绿（core / sb2 / sb3 / example / example-all / example-boot2）。

- [ ] **Step 2: README 增注解用法小节**

在"契约扫描"相关章节补：
```markdown
### 接口描述注解

Archimedes 提供自有描述注解（引入任一 starter 即可用，零额外依赖）：

- `@ApiModule(name, description)` — 标注 Controller / RPC 服务接口，定义模块分组
- `@ApiDoc(summary, description, deprecated)` — 标注接口方法
- `@ApiField(value, required, example)` — 标注参数与请求/响应体字段

描述信息在内置控制台展示。注意：本版本描述信息**只认自有注解**，不再读取
Swagger/Jackson 描述注解（validation 校验注解仍用于前端表单校验，不受影响）。
```

- [ ] **Step 3: 更新功能清单与锁定决策表**

`docs/功能清单与任务列表.md` 锁定决策表加一行：
```markdown
| 描述来源 | 接口/参数/字段描述**只认自有注解** `@ApiModule/@ApiDoc/@ApiField`（2026-07-08）；移除 Swagger/Jackson 描述读取，validation 校验线保留 |
```

- [ ] **Step 4: 提交**

```bash
git add README.md "docs/功能清单与任务列表.md"
git commit -m "docs: 补充自有注解用法与描述来源锁定决策"
```

- [ ] **Step 5（如走 opsx 流程）: 归档 change**

Run: `openspec validate api-annotations && openspec archive api-annotations --yes`
Expected: 主 spec 更新，change 归档。

---

## 完成标准

- `mvn test` 全模块全绿。
- 描述信息全部来自 `@ApiModule/@ApiDoc/@ApiField`，Swagger/Jackson 描述读取已移除。
- REST/RPC(4)/WS 契约均能携带 description。
- `example-all` 无 `io.swagger` 引用。
- Slice B（前端重构）作为独立 plan 后续编写，依赖本 slice 产出的 description/example 字段。
