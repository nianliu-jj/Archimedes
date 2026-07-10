# 统一响应包装体展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当项目用 `ResponseBodyAdvice` 统一包装返回值（如 `ResultVo{code,msg,data}`）时，通过配置声明让 Archimedes 把 REST `responseSchema` 呈现为完整包装体（外壳字段 + data 处嵌入方法真实返回类型）。

**Architecture:** 新增 `@NoApiWrapper` 注解 + `ArchimedesApiProperties.ResponseWrapper` 配置内部类 + `ResponseWrapperResolver`（scanner/schema 包，单一职责：把内层 schema 包进外壳）。`AbstractRestApiScanner` 基类用已持有的 `properties` 自建 resolver 实例，在 `buildApiInfo` 生成 `responseSchema` 后接入——Servlet/Reactive 子类共享，**无需改扫描器构造签名与两个 starter 的自动装配**。默认关闭，不配置则行为完全不变。

**Tech Stack:** Java 8 字节码（core `--release 8`）、反射 + `TypeSchemaResolver` 现有解析、JUnit 5 + AssertJ、单文件 ES5 前端（本特性前端零改动）、Maven 多模块。

## Global Constraints

- 回答与注释用中文；每个**新建** Java 文件类头 javadoc，`@author nianliu-jj`，`@since 2026-07-10`。
- 关键代码加详细中文注释；关键节点加日志（加载失败/字段缺失降级用 debug/warn）。
- `archimedes-core` 以 `--release 8` 编译，**不得** `import javax.servlet.*`/`jakarta.servlet.*`，不引入任何第三方依赖。
- 描述只认自有注解；validation 校验线与本特性正交、不动。
- 注解 `@Retention(RUNTIME)` + `@Documented`。
- 默认关闭（`enabled=false`），不配置 → 行为不变（向后兼容）。
- 配置键：`archimedes.api.response-wrapper.{enabled, wrapper-class, data-field}`（属性名 `enabled/wrapperClass/dataField`）。
- 每个 Task 结束提交，中文 commit message。用 `mvn` 运行（工程内 shim → maven 3.9.16，JDK 21）。

---

### Task 1: `@NoApiWrapper` 注解

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/NoApiWrapper.java`

**Interfaces:**
- Produces: `@interface NoApiWrapper {}`（`@Target({METHOD, TYPE})`, `@Retention(RUNTIME)`, `@Documented`）。

- [ ] **Step 1: 写 `NoApiWrapper.java`**

```java
package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 REST handler 方法或 Controller 类上，声明该接口（或整个控制器）
 * 的响应<b>不被统一响应包装体</b>包裹——契约展示时 responseSchema 保持方法真实返回类型，
 * 不套 {@code archimedes.api.response-wrapper.wrapper-class} 指定的外壳。
 * <p>语义等同宿主项目里常见的 {@code @NotControllerResponseAdvice}：用于本就直接返回
 * 包装体、或需绕过统一包装的端点。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoApiWrapper {
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q -pl archimedes-core compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/annotation/NoApiWrapper.java
git commit -m "feat: 新增 @NoApiWrapper 注解（声明接口不套统一响应包装体）"
```

---

### Task 2: `ResponseWrapper` 配置内部类

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/config/ArchimedesApiProperties.java`
- Test: `archimedes-core/src/test/java/io/github/nianliu/archimedes/config/ArchimedesApiPropertiesTest.java` (Create)

**Interfaces:**
- Produces: `ArchimedesApiProperties.getResponseWrapper(): ResponseWrapper`；`ResponseWrapper{ boolean isEnabled()/setEnabled; String getWrapperClass()/setWrapperClass; String getDataField()/setDataField }`（默认 `enabled=false`, `wrapperClass=""`, `dataField="data"`）。

- [ ] **Step 1: 在 `ArchimedesApiProperties` 增加字段与内部类**

在 `private SecurityScheme security = new SecurityScheme();` 之后新增字段：

```java
    /** 统一响应包装体展示配置（如项目用 ResponseBodyAdvice 把返回值包进 ResultVo）。 */
    private ResponseWrapper responseWrapper = new ResponseWrapper();
```

在 `getSecurity/setSecurity` 之后新增 getter/setter：

```java
    public ResponseWrapper getResponseWrapper() {
        return responseWrapper;
    }

    public void setResponseWrapper(ResponseWrapper responseWrapper) {
        this.responseWrapper = responseWrapper;
    }
```

在类内（`SecurityType` 枚举之前）新增内部类：

```java
    /**
     * 统一响应包装体配置：宿主项目常用 ResponseBodyAdvice 把 Controller 返回值统一包进外壳
     * （如 {@code ResultVo{code,msg,data}}）。静态扫描无法感知运行时包装，故由此显式声明，
     * 使 responseSchema 呈现完整包装体（data 处嵌入方法真实返回类型）。
     */
    public static class ResponseWrapper {

        /** 是否启用包装体展示；默认关闭，不配置则契约行为完全不变。 */
        private boolean enabled = false;

        /** 包装类全限定名（配置键 wrapper-class）；为空或加载不到则视为未启用。 */
        private String wrapperClass = "";

        /** data 字段名（包装类中承载真实返回对象的字段），默认 "data"。 */
        private String dataField = "data";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWrapperClass() {
            return wrapperClass;
        }

        public void setWrapperClass(String wrapperClass) {
            this.wrapperClass = wrapperClass;
        }

        public String getDataField() {
            return dataField;
        }

        public void setDataField(String dataField) {
            this.dataField = dataField;
        }
    }
```

- [ ] **Step 2: 写默认值失败测试**

Create `ArchimedesApiPropertiesTest.java`:

```java
package io.github.nianliu.archimedes.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArchimedesApiProperties 默认值单测（聚焦 response-wrapper 子配置）。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
class ArchimedesApiPropertiesTest {

    @Test
    void responseWrapperDefaults() {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        ArchimedesApiProperties.ResponseWrapper w = props.getResponseWrapper();
        assertThat(w).isNotNull();
        assertThat(w.isEnabled()).isFalse();
        assertThat(w.getWrapperClass()).isEmpty();
        assertThat(w.getDataField()).isEqualTo("data");
    }

    @Test
    void responseWrapperSettable() {
        ArchimedesApiProperties.ResponseWrapper w = new ArchimedesApiProperties.ResponseWrapper();
        w.setEnabled(true);
        w.setWrapperClass("com.demo.ResultVo");
        w.setDataField("payload");
        assertThat(w.isEnabled()).isTrue();
        assertThat(w.getWrapperClass()).isEqualTo("com.demo.ResultVo");
        assertThat(w.getDataField()).isEqualTo("payload");
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `mvn -q -pl archimedes-core test -Dtest=ArchimedesApiPropertiesTest`
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/config/ArchimedesApiProperties.java archimedes-core/src/test/java/io/github/nianliu/archimedes/config/ArchimedesApiPropertiesTest.java
git commit -m "feat: ArchimedesApiProperties 新增 response-wrapper 配置项"
```

---

### Task 3: `ResponseWrapperResolver` 核心逻辑

**Files:**
- Create: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/ResponseWrapperResolver.java`
- Test: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/ResponseWrapperResolverTest.java` (Create)

**Interfaces:**
- Consumes: `ArchimedesApiProperties`（Task 2）、`NoApiWrapper`（Task 1）、`TypeSchemaResolver.resolve(Type)`、`FieldInfo`。
- Produces: `new ResponseWrapperResolver(ArchimedesApiProperties)`；`FieldInfo wrap(FieldInfo innerSchema, java.lang.reflect.Method method, Class<?> controllerType)`。

- [ ] **Step 1: 写失败测试 `ResponseWrapperResolverTest.java`**

```java
package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResponseWrapperResolver 单测：包装组装（data 节点替换）、三类豁免、降级路径。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
class ResponseWrapperResolverTest {

    /** 测试用包装类：code/msg/data 三字段（data 承载真实返回对象）。 */
    static class ResultVo {
        private int code;
        private String msg;
        private Object data;
    }

    static class Payload {
        private String name;
        private int qty;
    }

    static class Handlers {
        Payload wrapped() { return null; }
        @NoApiWrapper
        Payload exemptByAnnotation() { return null; }
        ResultVo returnsWrapper() { return null; }
        ResponseEntity<Payload> returnsResponseEntity() { return null; }
        void nothing() { }
    }

    @NoApiWrapper
    static class ExemptController {
        Payload any() { return null; }
    }

    private ArchimedesApiProperties propsWith(String wrapperClass, String dataField) {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.getResponseWrapper().setEnabled(true);
        props.getResponseWrapper().setWrapperClass(wrapperClass);
        props.getResponseWrapper().setDataField(dataField);
        return props;
    }

    private static Method m(String name, Class<?>... args) throws Exception {
        return Handlers.class.getDeclaredMethod(name, args);
    }

    private static FieldInfo child(FieldInfo node, String name) {
        return node.getChildren().stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Test
    void wrapsInnerIntoDataField() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        FieldInfo wrapped = r.wrap(inner, m("wrapped"), Handlers.class);

        // 顶层变成包装类
        assertThat(wrapped.getType()).isEqualTo("ResultVo");
        assertThat(wrapped.getChildren()).extracting(FieldInfo::getName).contains("code", "msg", "data");
        // data 节点结构被替换为 Payload
        FieldInfo data = child(wrapped, "data");
        assertThat(data.getType()).isEqualTo("Payload");
        assertThat(data.getChildren()).extracting(FieldInfo::getName).containsExactlyInAnyOrder("name", "qty");
        // 外壳其余字段保留
        assertThat(child(wrapped, "code").getType()).isEqualTo("int");
    }

    @Test
    void exemptByNoApiWrapperMethod() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        FieldInfo out = r.wrap(inner, m("exemptByAnnotation"), Handlers.class);
        assertThat(out.getType()).isEqualTo("Payload"); // 未套壳
    }

    @Test
    void exemptByNoApiWrapperOnClass() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        Method any = ExemptController.class.getDeclaredMethod("any");
        FieldInfo out = r.wrap(inner, any, ExemptController.class);
        assertThat(out.getType()).isEqualTo("Payload");
    }

    @Test
    void exemptWhenReturnTypeIsWrapper() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(ResultVo.class);
        FieldInfo out = r.wrap(inner, m("returnsWrapper"), Handlers.class);
        assertThat(out.getType()).isEqualTo("ResultVo");
        // 未二次包装：data 仍是包装类原始 Object 叶子，不是被替换的结构
        assertThat(child(out, "data").getType()).isEqualTo("Object");
    }

    @Test
    void exemptWhenReturnTypeIsResponseEntity() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        // 内层已解包为 Payload（resolve 解包 ResponseEntity）
        FieldInfo inner = TypeSchemaResolver.resolve(
                m("returnsResponseEntity").getGenericReturnType());
        FieldInfo out = r.wrap(inner, m("returnsResponseEntity"), Handlers.class);
        assertThat(out.getType()).isEqualTo("Payload"); // 未套壳
    }

    @Test
    void disabledReturnsInner() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties(); // 默认关闭
        ResponseWrapperResolver r = new ResponseWrapperResolver(props);
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void wrapperClassNotLoadableReturnsInner() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith("com.nope.NotExist", "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void missingDataFieldReturnsInner() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "payload"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void voidInnerLeavesDataNodeAsIs() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        // 内层 void → resolve 返回 null
        FieldInfo out = r.wrap(null, m("nothing"), Handlers.class);
        assertThat(out.getType()).isEqualTo("ResultVo");
        assertThat(child(out, "data").getType()).isEqualTo("Object"); // data 原样
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl archimedes-core test -Dtest=ResponseWrapperResolverTest`
Expected: 编译失败（`ResponseWrapperResolver` 未定义）

- [ ] **Step 3: 写 `ResponseWrapperResolver.java`**

```java
package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 统一响应包装体解析器：当配置了 {@code archimedes.api.response-wrapper.wrapper-class} 时，
 * 把方法真实返回类型的字段树（innerSchema）嵌入包装类的 data 字段位置，
 * 返回完整包装体 FieldInfo；未启用/豁免/加载失败/无 data 字段时原样返回 innerSchema。
 *
 * <p>豁免（不套壳）三种：方法或 Controller 类标 {@code @NoApiWrapper}、
 * 返回类型即包装类或其子类、返回类型为 {@code ResponseEntity}。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
public class ResponseWrapperResolver {

    private static final Logger log = LoggerFactory.getLogger(ResponseWrapperResolver.class);

    /** ResponseEntity 的 FQCN（按字符串判断，避免对具体类型的强绑定）。 */
    private static final String RESPONSE_ENTITY = "org.springframework.http.ResponseEntity";

    private final ArchimedesApiProperties properties;

    public ResponseWrapperResolver(ArchimedesApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 把 innerSchema 包进配置的包装体；未启用/豁免/降级时原样返回 innerSchema。
     *
     * @param innerSchema    方法真实返回类型的字段树（可能为 null，表示 void 无响应体）
     * @param method         处理方法
     * @param controllerType 所属 Controller 类（用于类级 @NoApiWrapper 判定）
     * @return 完整包装体 FieldInfo，或原样 innerSchema
     */
    public FieldInfo wrap(FieldInfo innerSchema, Method method, Class<?> controllerType) {
        ArchimedesApiProperties.ResponseWrapper cfg = properties.getResponseWrapper();
        // 1) 未启用 / 未配置包装类 → 原样返回
        if (cfg == null || !cfg.isEnabled() || cfg.getWrapperClass() == null || cfg.getWrapperClass().isEmpty()) {
            return innerSchema;
        }
        // 2) 豁免判定
        if (isExempt(method, controllerType, cfg.getWrapperClass())) {
            return innerSchema;
        }
        // 3) 加载包装类；失败视为未启用（不硬失败）
        Class<?> wrapperClass = loadClass(cfg.getWrapperClass());
        if (wrapperClass == null) {
            log.debug("Archimedes: 响应包装类 {} 加载失败，responseSchema 保持内层结构", cfg.getWrapperClass());
            return innerSchema;
        }
        // 4) 解析包装类字段树，把 data 字段的结构替换为 innerSchema
        FieldInfo wrapperTree = TypeSchemaResolver.resolve(wrapperClass);
        if (wrapperTree == null || wrapperTree.getChildren() == null) {
            return innerSchema;
        }
        FieldInfo dataNode = findChild(wrapperTree, cfg.getDataField());
        if (dataNode == null) {
            log.warn("Archimedes: 响应包装类 {} 中不存在 data 字段 '{}'，responseSchema 保持内层结构",
                    cfg.getWrapperClass(), cfg.getDataField());
            return innerSchema;
        }
        // innerSchema 为 null（void 内层）时不替换，data 节点保持包装类原样
        if (innerSchema != null) {
            dataNode.setType(innerSchema.getType());
            dataNode.setArray(innerSchema.isArray());
            dataNode.setChildren(innerSchema.getChildren());
            dataNode.setEnumValues(innerSchema.getEnumValues());
        }
        return wrapperTree;
    }

    /** 三类豁免判定。 */
    private boolean isExempt(Method method, Class<?> controllerType, String wrapperClassName) {
        // (a) 方法或类标注 @NoApiWrapper
        if (method.isAnnotationPresent(NoApiWrapper.class)
                || (controllerType != null && controllerType.isAnnotationPresent(NoApiWrapper.class))) {
            return true;
        }
        Class<?> returnType = method.getReturnType();
        // (b) 返回类型是 ResponseEntity（自定义状态码，绕过 advice）
        if (RESPONSE_ENTITY.equals(returnType.getName())) {
            return true;
        }
        // (c) 返回类型本身就是包装类或其子类（本就直接返回壳，不二次包装）
        Class<?> wrapperClass = loadClass(wrapperClassName);
        return wrapperClass != null && wrapperClass.isAssignableFrom(returnType);
    }

    /** 在字段树的直接子节点中按名查找；无则 null。 */
    private FieldInfo findChild(FieldInfo tree, String name) {
        if (tree.getChildren() == null || name == null) {
            return null;
        }
        for (FieldInfo c : tree.getChildren()) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    /** 按 FQCN 加载类；失败返回 null（不抛）。 */
    private Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn, false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl archimedes-core test -Dtest=ResponseWrapperResolverTest`
Expected: PASS（9 tests）

- [ ] **Step 5: 提交**

```bash
git add archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/schema/ResponseWrapperResolver.java archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/schema/ResponseWrapperResolverTest.java
git commit -m "feat: ResponseWrapperResolver 把内层 schema 包进统一响应包装体"
```

---

### Task 4: 接入 `AbstractRestApiScanner.buildApiInfo`

**Files:**
- Modify: `archimedes-core/src/main/java/io/github/nianliu/archimedes/scanner/AbstractRestApiScanner.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java`
- Modify: `archimedes-core/src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`

**Interfaces:**
- Consumes: `ResponseWrapperResolver`（Task 3）。
- Produces: `buildApiInfo` 生成的 `responseSchema` 经 `wrap(...)` 后可为包装体。

- [ ] **Step 1: 基类持有并接入 resolver**

在 `AbstractRestApiScanner` 补 import：

```java
import io.github.nianliu.archimedes.scanner.schema.ResponseWrapperResolver;
```

在字段区（`cache` 之后）新增：

```java
    /** 统一响应包装体解析器：由本基类用 properties 自建，Servlet/Reactive 子类共享（无需改子类构造）。 */
    private final ResponseWrapperResolver responseWrapperResolver;
```

在构造器 `this.properties = properties;` 之后新增：

```java
        this.responseWrapperResolver = new ResponseWrapperResolver(properties);
```

把 `buildApiInfo` 里的：

```java
        info.setResponseSchema(TypeSchemaResolver.resolve(method.getGenericReturnType()));
```

改为：

```java
        // 契约增强：响应体字段结构；若配置了统一响应包装体，则把内层结构嵌入包装壳的 data 字段
        FieldInfo innerResponseSchema = TypeSchemaResolver.resolve(method.getGenericReturnType());
        info.setResponseSchema(responseWrapperResolver.wrap(
                innerResponseSchema, method, controllerType));
```

> 注意：`controllerType` 变量在 `buildApiInfo` 中已存在（`Class<?> controllerType = handlerMethod.getBeanType();`），但其声明在 `setResponseSchema` **之后**。需把该声明上移到 `setResponseSchema` 调用**之前**（即紧接 `setDeprecated(...)` 之后声明 `controllerType`），再在 tag 相关调用与 wrap 调用中复用同一变量。确认 `FieldInfo` 已 import（文件已有）。

- [ ] **Step 2: 给 `SampleControllers` 增加包装演示控制器**

在 `SampleControllers` 内新增：一个统一响应包装类 `ResultVo`（作为配置的 wrapper-class），
一个 `WrapController`（含普通端点与 `@NoApiWrapper` 端点，均返回 `List<String>`）：

```java
    /** 测试用统一响应包装类。 */
    public static class ResultVo {
        public int code;
        public String msg;
        public Object data;
    }

    @RestController
    @RequestMapping("/api/wrap")
    public static class WrapController {

        @GetMapping("/list")
        public java.util.List<String> listEndpoint() {
            return java.util.List.of();
        }

        @io.github.nianliu.archimedes.annotation.NoApiWrapper
        @GetMapping("/raw")
        public java.util.List<String> rawEndpoint() {
            return java.util.List.of();
        }
    }
```

- [ ] **Step 3: 在 `RestApiScannerTest` 增加包装断言**

新增一个用启用了 wrapper 的 properties 构造 scanner 的辅助与测试：

```java
    private RestApiScanner wrappedScannerFor(Class<?>... controllers) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controllers);
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.getResponseWrapper().setEnabled(true);
        props.getResponseWrapper().setWrapperClass(SampleControllers.ResultVo.class.getName());
        props.getResponseWrapper().setDataField("data");
        return new RestApiScanner(List.of(mapping), props);
    }

    @Test
    void responseSchemaWrappedIntoResultVo() {
        List<ApiInfo> apis = wrappedScannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo list = find(apis, "/api/wrap/list");
        // 顶层为包装类 ResultVo，data 处为 List<String>（array=true, type=String）
        assertThat(list.getResponseSchema().getType()).isEqualTo("ResultVo");
        FieldInfo data = list.getResponseSchema().getChildren().stream()
                .filter(c -> c.getName().equals("data")).findFirst().orElseThrow();
        assertThat(data.isArray()).isTrue();
        assertThat(data.getType()).isEqualTo("String");
    }

    @Test
    void noApiWrapperEndpointNotWrapped() {
        List<ApiInfo> apis = wrappedScannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo raw = find(apis, "/api/wrap/raw");
        // @NoApiWrapper：responseSchema 保持内层 List<String>（array=true, type=String），不套壳
        assertThat(raw.getResponseSchema().getType()).isEqualTo("String");
        assertThat(raw.getResponseSchema().isArray()).isTrue();
    }

    @Test
    void defaultScannerDoesNotWrap() {
        // 默认（未启用 wrapper）→ responseSchema 保持内层结构，向后兼容
        List<ApiInfo> apis = scannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo list = find(apis, "/api/wrap/list");
        assertThat(list.getResponseSchema().getType()).isEqualTo("String");
        assertThat(list.getResponseSchema().isArray()).isTrue();
    }
```

> 补 import（若缺）：`import io.github.nianliu.archimedes.model.FieldInfo;`。

- [ ] **Step 4: 运行相关测试**

Run: `mvn -q -pl archimedes-core test -Dtest=RestApiScannerTest,ResponseWrapperResolverTest,SchemaFacadeTest`
Expected: PASS（现有测试不受影响——默认 properties 未启用 wrapper，`responseSchema` 行为不变）

- [ ] **Step 5: 全量 core 测试确认无回归**

Run: `mvn -q -pl archimedes-core test 2>&1 | grep -E "Tests run: [0-9]+, Failures: [1-9]|Errors: [1-9]|BUILD"`
Expected: 无非零 Failures/Errors，BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: REST 扫描器接入统一响应包装体（buildApiInfo 套壳 responseSchema）"
```

---

### Task 5: example-all 演示 + e2e + 文档 + 真机验证

**Files:**
- Create: `example-all/src/main/java/io/github/nianliu/archimedes/exampleall/wrapper/ResultVo.java`
- Create: `example-all/src/main/java/io/github/nianliu/archimedes/exampleall/wrapper/GlobalResponseAdvice.java`
- Create: `example-all/src/main/java/io/github/nianliu/archimedes/exampleall/wrapper/WrapperDemoController.java`
- Modify: `example-all/src/main/resources/application.yml`
- Modify: `example-all/src/test/java/io/github/nianliu/archimedes/exampleall/AllFeaturesEndToEndTest.java`
- Modify: `docs/功能清单与任务列表.md`

**Interfaces:**
- Consumes: 全链路成品。

- [ ] **Step 1: 写 `ResultVo`**

```java
package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.ApiField;

/**
 * 统一响应包装体演示：ResponseBodyAdvice 把 Controller 返回值包进本类。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
public class ResultVo {

    @ApiField("状态码")
    private int code;

    @ApiField("状态信息")
    private String msg;

    @ApiField("业务数据")
    private Object data;

    public ResultVo() {
    }

    public ResultVo(Object data) {
        this.code = 200;
        this.msg = "OK";
        this.data = data;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
```

- [ ] **Step 2: 写 `GlobalResponseAdvice`**

```java
package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应包装：把 Controller 返回值包进 {@link ResultVo}。
 * 已是 ResultVo、或标了 {@link NoApiWrapper} 的接口不包装——与 Archimedes 契约展示的豁免规则一致。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 返回类型已是 ResultVo、或方法/类标了 @NoApiWrapper 的不包装
        if (ResultVo.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        return !returnType.hasMethodAnnotation(NoApiWrapper.class)
                && returnType.getDeclaringClass().getAnnotation(NoApiWrapper.class) == null;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return new ResultVo(body);
    }
}
```

> 说明：为演示简洁，String 返回值的特例包装（用户原示例里的 ObjectMapper 分支）此处省略——演示端点不返回裸 String。若需覆盖，端点返回对象/集合即可。

- [ ] **Step 3: 写 `WrapperDemoController`**

```java
package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 统一响应包装体演示：普通端点被 ResultVo 包裹，@NoApiWrapper 端点保持裸结构。
 * 打开 UI 对照两者的响应字段树。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@RestController
@RequestMapping("/api/wrapper")
@ApiModule(name = "响应包装演示", description = "演示统一响应包装体在契约中的展示与 @NoApiWrapper 豁免")
public class WrapperDemoController {

    /** 被 ResultVo 包裹：契约 responseSchema 顶层应为 ResultVo，data 处为 Item 结构。 */
    @ApiDoc(summary = "查询条目（被包装）", description = "返回值经 ResponseBodyAdvice 包进 ResultVo")
    @GetMapping("/items")
    public List<Item> items() {
        return Arrays.asList(new Item(1L, "键盘"), new Item(2L, "鼠标"));
    }

    /** 豁免：契约 responseSchema 保持裸 Item 结构。 */
    @ApiDoc(summary = "查询条目（不包装）", description = "标 @NoApiWrapper，响应不套 ResultVo")
    @NoApiWrapper
    @GetMapping("/items-raw")
    public List<Item> itemsRaw() {
        return Arrays.asList(new Item(3L, "显示器"));
    }

    /** 演示用条目。 */
    public static class Item {
        private Long id;
        private String name;

        public Item() {
        }

        public Item(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
```

- [ ] **Step 4: 配置 application.yml**

在 `example-all/src/main/resources/application.yml` 的 `archimedes.api` 下新增（若无 `archimedes.api` 段则新建）：

```yaml
archimedes:
  api:
    response-wrapper:
      enabled: true
      wrapper-class: io.github.nianliu.archimedes.exampleall.wrapper.ResultVo
      data-field: data
```

> 先 Read application.yml 确认现有结构，把 `response-wrapper` 挂到既有 `archimedes.api` 节点下（保持缩进），不要另起重复的 `archimedes` 根键。

- [ ] **Step 5: 加 e2e 断言**

在 `AllFeaturesEndToEndTest` 新增（复用类内既有 `restApis()` 辅助获取 `/apis`）：

```java
    @Test
    @SuppressWarnings("unchecked")
    void wrappedEndpointShowsResultVoSchema() {
        List<Map<String, Object>> rest = restApis();
        Map<String, Object> items = rest.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/wrapper/items"))
                .findFirst().orElseThrow();
        Map<String, Object> schema = (Map<String, Object>) items.get("responseSchema");
        assertThat(schema.get("type")).isEqualTo("ResultVo");
        List<Map<String, Object>> children = (List<Map<String, Object>>) schema.get("children");
        assertThat(children).extracting(c -> c.get("name")).contains("code", "msg", "data");
        Map<String, Object> data = children.stream()
                .filter(c -> c.get("name").equals("data")).findFirst().orElseThrow();
        assertThat(data.get("type")).isEqualTo("Item");
        assertThat(data.get("array")).isEqualTo(true);

        // @NoApiWrapper 端点：responseSchema 保持裸 Item（不套 ResultVo）
        Map<String, Object> raw = rest.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/wrapper/items-raw"))
                .findFirst().orElseThrow();
        Map<String, Object> rawSchema = (Map<String, Object>) raw.get("responseSchema");
        assertThat(rawSchema.get("type")).isEqualTo("Item");
    }
```

> 若 `AllFeaturesEndToEndTest` 无 `restApis()` 私有辅助，内联 `rest.getForObject("/archimedes/apis", Map.class)` 取 `restApis` 列表（`rest` 为类中既有 `TestRestTemplate`）。先 Read 该测试类确认。

- [ ] **Step 6: 运行 example-all e2e**

Run: `mvn -q -pl example-all -am install -DskipTests && mvn -q -pl example-all test -Dtest=AllFeaturesEndToEndTest 2>&1 | grep -E "Tests run|BUILD"`
Expected: PASS

- [ ] **Step 7: 更新文档**

在 `docs/功能清单与任务列表.md` 的「接口契约扫描与展示 / 契约增强」条目补一句「统一响应包装体展示：配置 `archimedes.api.response-wrapper.*` 后，responseSchema 呈现完整包装体（data 处嵌入真实返回类型）；`@NoApiWrapper`/返回包装类/ResponseEntity 自动豁免」；配置项区补 `response-wrapper.{enabled,wrapper-class,data-field}`。保持行文风格。

- [ ] **Step 8: 全量回归**

Run: `mvn test 2>&1 | grep -E "archimedes-.*(SUCCESS|FAILURE)|BUILD SUCCESS|BUILD FAILURE|Failures: [1-9]|Errors: [1-9]"`
Expected: 各模块 SUCCESS

- [ ] **Step 9: 真机验证**

```bash
netstat -ano | grep ':8082' | grep LISTENING | awk '{print $NF}' | while read p; do taskkill //PID $p //F; done
cd /d/Archimedes/example-all && nohup java -jar target/example-all-1.1-SNAPSHOT.jar > /tmp/exall.log 2>&1 &
```

就绪后校验：

```bash
curl -s http://localhost:8082/archimedes/apis | python -c "
import json,sys
d=json.load(sys.stdin)
for a in d['restApis']:
    p=a.get('paths',[])
    if '/api/wrapper/items' in p:
        s=a['responseSchema']; print('items schema type=',s['type'],'children=',[c['name'] for c in s.get('children',[])])
        data=[c for c in s.get('children',[]) if c['name']=='data'][0]; print('  data type=',data['type'],'array=',data['array'])
    if '/api/wrapper/items-raw' in p:
        print('items-raw schema type=',a['responseSchema']['type'])
"
```

Expected: items 的 `responseSchema.type=ResultVo`、data.type=Item/array=True；items-raw 的 `type=Item`。随后 `taskkill` 停应用。

- [ ] **Step 10: 提交**

```bash
git add -A
git commit -m "test: example-all 统一响应包装演示 + e2e；docs: 补充 response-wrapper 说明"
```

---

## Self-Review 结论

- **Spec 覆盖**：感知方式=配置(T2) · @NoApiWrapper(T1) · 组装 data 替换(T3) · 三类豁免(T3) · 接入 buildApiInfo(T4) · 降级(加载失败/字段缺失/void)(T3) · 演示+e2e+文档+真机(T5) · @ApiResponse 不套壳(设计非目标，无需代码) —— 全部有对应任务。
- **类型一致**：`ResponseWrapperResolver(ArchimedesApiProperties)` / `wrap(FieldInfo, Method, Class<?>)` / `ResponseWrapper.isEnabled/getWrapperClass/getDataField` 在各任务间签名一致；`FieldInfo` 的 setType/setArray/setChildren/setEnumValues 均为现有 setter。
- **无占位符**：每个代码步骤给出完整代码；T4 Step2 的 SampleControllers 演示已修正为最终 `WrapController`（去掉 `__Marker` 误笔）；T4 Step3 提示删除占位行。
- **绿色边界**：T1/T2/T3 各自独立绿；T4 默认 properties 不启用 wrapper→现有测试不变；T5 example-all 启用配置并加 e2e。
- **向后兼容**：默认关闭，接入点仅在 enabled 时改变 responseSchema；`responseSchema` 仍是 FieldInfo 树，前端零改动、watch 签名已含。
