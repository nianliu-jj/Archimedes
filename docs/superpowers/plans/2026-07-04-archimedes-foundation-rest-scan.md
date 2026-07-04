# Archimedes 切片一实现计划：地基 + REST 接口扫描

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让任意 Spring Boot 3.x（Servlet MVC）项目引入 `archimedes` 依赖后，零配置暴露一个 `/archimedes/apis` JSON 端点与一个 `/archimedes` 极简网页，列出全部 REST 接口。

**Architecture:** 自动装配注册一个只读扫描器 `RestApiScanner`，它复用 Spring 已算好的 `RequestMappingHandlerMapping.getHandlerMethods()` 把每个接口转成统一 `ApiInfo` 模型（首次访问时扫描并缓存）；一个普通 `@RestController` 暴露 JSON，并从 classpath 读取内置 HTML、注入绝对 API 地址后返回。

**Tech Stack:** Java 17（`--release 17`，JDK 21 构建）、Maven、Spring Boot 3.3.5（`spring-boot-dependencies` BOM）、JUnit 5 + AssertJ + Mockito + Spring Test。

## Global Constraints

- 包根：`io.github.nianliu.archimedes`；配置前缀：`archimedes.api`；端点根路径默认 `/archimedes`（`archimedes.api.base-path`）。
- Spring Boot：本切编译目标 **Java 17（`maven.compiler.release=17`）**、依赖 **Boot 3.3.5**，在 **JDK 21** 上构建与测试。经真机认证的 Boot 2.x 支持不在本切范围。
- 扫描代码只允许使用在 Spring 5.3 与 6.x 中签名一致的 Web API（保证逻辑上 2.x 就绪）：`RequestMappingHandlerMapping.getHandlerMethods()`、`RequestMappingInfo.getPathPatternsCondition()/getPatternsCondition()/getMethodsCondition()/getConsumesCondition()/getProducesCondition()`、`HandlerMethod`、`MethodParameter`。**禁止**手写 `@RequestMapping` 反射拼路径。
- 路径提取**必须**先取 `getPathPatternsCondition()`，为空再回退 `getPatternsCondition()`。
- 暴露方式：普通 `@RestController`；不引入 actuator；不做鉴权（交给用户 security）。
- 依赖策略：`spring-boot-autoconfigure`、`spring-webmvc`、`slf4j-api` 均 `optional=true`，继承使用方版本。
- 自动装配仅通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册（不用 `spring.factories`）。
- 前置条件：命令行可用 `mvn`（3.6+）与 JDK 17+（本机 JDK 21）。
- 全程 TDD、频繁提交；DRY、YAGNI。
- 每步命令示例用 `-q`（安静模式）；成功判据为 `BUILD SUCCESS` 且 `Tests run: N, Failures: 0, Errors: 0`。

---

## 文件结构

**生产代码（`src/main/java/io/github/nianliu/archimedes/`）**
- `model/ParamSource.java` — 参数来源枚举
- `model/ParamInfo.java` — 单个参数信息
- `model/ApiInfo.java` — 单个接口信息
- `config/ArchimedesApiProperties.java` — `@ConfigurationProperties("archimedes.api")`
- `config/ArchimedesAutoConfiguration.java` — 自动装配
- `scanner/RestApiScanner.java` — 扫描器（读 `RequestMappingHandlerMapping`）
- `web/ArchimedesApiController.java` — JSON + UI 端点

**生产资源（`src/main/resources/`）**
- `archimedes-ui/index.html` — 内置极简页面（含占位符 `__ARCHIMEDES_API_URL__`）
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 装配注册

**测试代码（`src/test/java/io/github/nianliu/archimedes/`）**
- `BuildSmokeTest.java`（Task 1）
- `model/ApiModelTest.java`（Task 2）
- `config/ArchimedesApiPropertiesTest.java`（Task 3）
- `scanner/PathExtractionTest.java`（Task 4）
- `scanner/SampleControllers.java` + `scanner/RestApiScannerTest.java`（Task 5）
- `scanner/RestApiScannerExclusionTest.java`（Task 6）
- `web/ArchimedesApiControllerTest.java`（Task 7）
- `config/ArchimedesAutoConfigurationTest.java`（Task 8）
- `EndToEndTest.java`（Task 9）

**删除**：`src/main/java/io/github/nianliu/App.java`、`src/test/java/io/github/nianliu/AppTest.java`（archetype 残留，依赖 junit 3.8.1）。

---

## Task 1: 构建地基（pom + 清理 + 冒烟测试）

**Files:**
- Modify: `pom.xml`（全量替换）
- Delete: `src/main/java/io/github/nianliu/App.java`, `src/test/java/io/github/nianliu/AppTest.java`
- Test: `src/test/java/io/github/nianliu/archimedes/BuildSmokeTest.java`

**Interfaces:**
- Produces: 一个可用的 Maven 构建（Java 17 / Boot 3.3.5 BOM / JUnit5 via surefire 3.x / `-parameters` 开启），供后续所有 Task 使用。

- [ ] **Step 1: 删除 archetype 残留**

```bash
git rm src/main/java/io/github/nianliu/App.java src/test/java/io/github/nianliu/AppTest.java
```

- [ ] **Step 2: 全量替换 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.nianliu</groupId>
    <artifactId>archimedes</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>archimedes</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>17</maven.compiler.release>
        <spring-boot.version>3.3.5</spring-boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webmvc</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 写冒烟测试**

`src/test/java/io/github/nianliu/archimedes/BuildSmokeTest.java`:
```java
package io.github.nianliu.archimedes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {

    @Test
    void toolchainIsWired() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(17);
    }
}
```

- [ ] **Step 4: 运行，确认构建与 JUnit5 打通**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`；`Tests run: 1, Failures: 0, Errors: 0`。（首次会下载 Boot 3.3.5 依赖。）

- [ ] **Step 5: 提交**

```bash
git add pom.xml src/test/java/io/github/nianliu/archimedes/BuildSmokeTest.java
git commit -m "build: set up Boot 3.3 + Java 17 Maven build with JUnit 5"
```

---

## Task 2: 数据模型（ParamSource / ParamInfo / ApiInfo）

**Files:**
- Create: `src/main/java/io/github/nianliu/archimedes/model/ParamSource.java`
- Create: `src/main/java/io/github/nianliu/archimedes/model/ParamInfo.java`
- Create: `src/main/java/io/github/nianliu/archimedes/model/ApiInfo.java`
- Test: `src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java`

**Interfaces:**
- Produces:
  - `enum ParamSource { QUERY, PATH, BODY, HEADER, FORM, OTHER }`
  - `ParamInfo(String name, ParamSource source, String type, boolean required)` + 无参构造 + getters/setters
  - `ApiInfo`：无参构造 + 字段 `controllerClass:String, handlerMethod:String, httpMethods:List<String>, paths:List<String>, params:List<ParamInfo>, returnType:String, consumes:List<String>, produces:List<String>, deprecated:boolean` 的 getters/setters

- [ ] **Step 1: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/model/ApiModelTest.java`:
```java
package io.github.nianliu.archimedes.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelTest {

    @Test
    void apiInfoHoldsValues() {
        ParamInfo id = new ParamInfo("id", ParamSource.PATH, "java.lang.Long", true);

        ApiInfo api = new ApiInfo();
        api.setControllerClass("com.example.UserController");
        api.setHandlerMethod("getUser");
        api.setHttpMethods(List.of("GET"));
        api.setPaths(List.of("/api/users/{id}"));
        api.setParams(List.of(id));
        api.setReturnType("com.example.User");
        api.setDeprecated(true);

        assertThat(api.getControllerClass()).isEqualTo("com.example.UserController");
        assertThat(api.getPaths()).containsExactly("/api/users/{id}");
        assertThat(api.isDeprecated()).isTrue();
        assertThat(api.getParams()).hasSize(1);
        assertThat(api.getParams().get(0).getSource()).isEqualTo(ParamSource.PATH);
        assertThat(api.getParams().get(0).getName()).isEqualTo("id");
        assertThat(api.getParams().get(0).isRequired()).isTrue();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=ApiModelTest test`
Expected: 编译失败 `cannot find symbol: class ParamSource / ParamInfo / ApiInfo`。

- [ ] **Step 3: 写实现**

`model/ParamSource.java`:
```java
package io.github.nianliu.archimedes.model;

/** 参数来源。FORM 为后续切片预留，切片一不产出。 */
public enum ParamSource {
    QUERY, PATH, BODY, HEADER, FORM, OTHER
}
```

`model/ParamInfo.java`:
```java
package io.github.nianliu.archimedes.model;

public class ParamInfo {

    private String name;
    private ParamSource source;
    private String type;
    private boolean required;

    public ParamInfo() {
    }

    public ParamInfo(String name, ParamSource source, String type, boolean required) {
        this.name = name;
        this.source = source;
        this.type = type;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParamSource getSource() {
        return source;
    }

    public void setSource(ParamSource source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
```

`model/ApiInfo.java`:
```java
package io.github.nianliu.archimedes.model;

import java.util.List;

public class ApiInfo {

    private String controllerClass;
    private String handlerMethod;
    private List<String> httpMethods;
    private List<String> paths;
    private List<ParamInfo> params;
    private String returnType;
    private List<String> consumes;
    private List<String> produces;
    private boolean deprecated;

    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }

    public List<String> getHttpMethods() {
        return httpMethods;
    }

    public void setHttpMethods(List<String> httpMethods) {
        this.httpMethods = httpMethods;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<ParamInfo> getParams() {
        return params;
    }

    public void setParams(List<ParamInfo> params) {
        this.params = params;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public void setConsumes(List<String> consumes) {
        this.consumes = consumes;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=ApiModelTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/model src/test/java/io/github/nianliu/archimedes/model
git commit -m "feat: add ApiInfo/ParamInfo/ParamSource data model"
```

---

## Task 3: 配置属性（ArchimedesApiProperties）

**Files:**
- Create: `src/main/java/io/github/nianliu/archimedes/config/ArchimedesApiProperties.java`
- Test: `src/test/java/io/github/nianliu/archimedes/config/ArchimedesApiPropertiesTest.java`

**Interfaces:**
- Produces: `ArchimedesApiProperties`，前缀 `archimedes.api`，字段与默认值：`enabled=true`、`basePath="/archimedes"`、`uiEnabled=true`、`basePackages=[]`；标准 getters/setters（`isEnabled/setEnabled`、`getBasePath/setBasePath`、`isUiEnabled/setUiEnabled`、`getBasePackages/setBasePackages`）。

- [ ] **Step 1: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/config/ArchimedesApiPropertiesTest.java`:
```java
package io.github.nianliu.archimedes.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaults() {
        runner.run(context -> {
            ArchimedesApiProperties props = context.getBean(ArchimedesApiProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getBasePath()).isEqualTo("/archimedes");
            assertThat(props.isUiEnabled()).isTrue();
            assertThat(props.getBasePackages()).isEmpty();
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                "archimedes.api.enabled=false",
                "archimedes.api.base-path=/custom",
                "archimedes.api.ui-enabled=false",
                "archimedes.api.base-packages=com.a,com.b"
        ).run(context -> {
            ArchimedesApiProperties props = context.getBean(ArchimedesApiProperties.class);
            assertThat(props.isEnabled()).isFalse();
            assertThat(props.getBasePath()).isEqualTo("/custom");
            assertThat(props.isUiEnabled()).isFalse();
            assertThat(props.getBasePackages()).containsExactly("com.a", "com.b");
        });
    }

    @EnableConfigurationProperties(ArchimedesApiProperties.class)
    static class TestConfig {
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=ArchimedesApiPropertiesTest test`
Expected: 编译失败 `cannot find symbol: class ArchimedesApiProperties`。

- [ ] **Step 3: 写实现**

`config/ArchimedesApiProperties.java`:
```java
package io.github.nianliu.archimedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "archimedes.api")
public class ArchimedesApiProperties {

    /** 总开关。 */
    private boolean enabled = true;

    /** 端点根路径；JSON = {basePath}/apis，UI = {basePath}。 */
    private String basePath = "/archimedes";

    /** 是否在 {basePath} 挂载内置页面。 */
    private boolean uiEnabled = true;

    /** 非空时只扫描这些包前缀下的 Controller。 */
    private List<String> basePackages = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public boolean isUiEnabled() {
        return uiEnabled;
    }

    public void setUiEnabled(boolean uiEnabled) {
        this.uiEnabled = uiEnabled;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=ArchimedesApiPropertiesTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/config/ArchimedesApiProperties.java src/test/java/io/github/nianliu/archimedes/config/ArchimedesApiPropertiesTest.java
git commit -m "feat: add ArchimedesApiProperties configuration"
```

---

## Task 4: 扫描器——跨版本路径提取（extractPaths）

**Files:**
- Create: `src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java`（本任务只含字段、构造器、`static extractPaths`）
- Test: `src/test/java/io/github/nianliu/archimedes/scanner/PathExtractionTest.java`

**Interfaces:**
- Consumes: `ArchimedesApiProperties`（Task 3）、`ApiInfo`（Task 2）。
- Produces:
  - `RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties)` 构造器与字段（供 Task 5/6 扩展）。
  - `static List<String> extractPaths(RequestMappingInfo mappingInfo)`：先 `getPathPatternsCondition()`，为空回退 `getPatternsCondition()`，结果按字典序排序。

- [ ] **Step 1: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/scanner/PathExtractionTest.java`:
```java
package io.github.nianliu.archimedes.scanner;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;

class PathExtractionTest {

    @Test
    void extractsAntPatterns() {
        // 默认 builder = PatternsRequestCondition（Boot 2 默认的 Ant 风格）
        RequestMappingInfo info = RequestMappingInfo.paths("/b", "/a").build();

        assertThat(RestApiScanner.extractPaths(info)).containsExactly("/a", "/b");
    }

    @Test
    void extractsPathPatterns() {
        // 显式 PathPatternParser = PathPatternsRequestCondition（Boot 3 默认）
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        RequestMappingInfo info = RequestMappingInfo.paths("/modern/{id}").options(config).build();

        assertThat(RestApiScanner.extractPaths(info)).containsExactly("/modern/{id}");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=PathExtractionTest test`
Expected: 编译失败 `cannot find symbol: class RestApiScanner`。

- [ ] **Step 3: 写实现（本任务仅骨架 + extractPaths）**

`scanner/RestApiScanner.java`:
```java
package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final ArchimedesApiProperties properties;

    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        this.handlerMappings = handlerMappings;
        this.properties = properties;
    }

    /**
     * 提取合并后的路径。跨版本关键点：Boot 3（或 Boot 2 开启 PathPattern）用
     * PathPatternsRequestCondition；Boot 2 默认用 PatternsRequestCondition。两者取其一非空。
     */
    static List<String> extractPaths(RequestMappingInfo mappingInfo) {
        PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
        if (pathPatterns != null && !pathPatterns.getPatterns().isEmpty()) {
            return pathPatterns.getPatterns().stream()
                    .map(PathPattern::getPatternString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        PatternsRequestCondition patterns = mappingInfo.getPatternsCondition();
        if (patterns != null && !patterns.getPatterns().isEmpty()) {
            return patterns.getPatterns().stream()
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
```

> 注：`log`、`handlerMappings`、`properties` 字段在本任务暂未使用，Task 5/6 会用到；如启用 `-Werror` 请忽略未使用告警（本 pom 未开启）。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=PathExtractionTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java src/test/java/io/github/nianliu/archimedes/scanner/PathExtractionTest.java
git commit -m "feat: add cross-version path extraction to RestApiScanner"
```

---

## Task 5: 扫描器——从 Controller 生成 ApiInfo + 缓存

**Files:**
- Modify: `src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java`（新增 `scan()`、`doScan()`、`toApiInfo()`、参数解析辅助）
- Create: `src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java`（测试样例，供 Task 6 复用）
- Test: `src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`

**Interfaces:**
- Consumes: `RestApiScanner(List<RequestMappingHandlerMapping>, ArchimedesApiProperties)`、`extractPaths`（Task 4）。
- Produces:
  - `List<ApiInfo> scan()`：首次扫描并用 `AtomicReference` 缓存；再次调用返回同一不可变列表实例。
  - 测试支撑 `SampleControllers.UserController`（`@RestController @RequestMapping("/api/users")`，含 `getUser/create/legacy` 三个方法）、`SampleControllers.buildMapping(Class<?>...)`（构造并 `afterPropertiesSet()` 一个 `RequestMappingHandlerMapping`）。

- [ ] **Step 1: 写测试样例支撑类**

`src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java`:
```java
package io.github.nianliu.archimedes.scanner;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/** 测试用样例 Controller 与构造 RequestMappingHandlerMapping 的辅助方法。 */
public final class SampleControllers {

    private SampleControllers() {
    }

    @RestController
    @RequestMapping("/api/users")
    public static class UserController {

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
    }

    /** 用给定 Controller 类构造并初始化一个 RequestMappingHandlerMapping。 */
    public static RequestMappingHandlerMapping buildMapping(Class<?>... controllers) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        for (Class<?> controller : controllers) {
            context.register(controller);
        }
        context.refresh();

        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(context);
        mapping.afterPropertiesSet();
        return mapping;
    }
}
```

- [ ] **Step 2: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java`:
```java
package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiScannerTest {

    private RestApiScanner scannerFor(Class<?>... controllers) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controllers);
        return new RestApiScanner(List.of(mapping), new ArchimedesApiProperties());
    }

    private ApiInfo find(List<ApiInfo> apis, String path) {
        return apis.stream()
                .filter(a -> a.getPaths().contains(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no api for " + path));
    }

    @Test
    void scansPathsMethodsAndReturnType() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        ApiInfo getUser = find(apis, "/api/users/{id}");
        assertThat(getUser.getHttpMethods()).containsExactly("GET");
        assertThat(getUser.getControllerClass()).endsWith("UserController");
        assertThat(getUser.getHandlerMethod()).isEqualTo("getUser");
        assertThat(getUser.getReturnType()).isEqualTo("java.lang.String");
    }

    @Test
    void scansParametersWithSource() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        List<ParamInfo> params = find(apis, "/api/users/{id}").getParams();
        assertThat(params).extracting(ParamInfo::getName).contains("id", "filter");
        ParamInfo id = params.stream().filter(p -> p.getName().equals("id")).findFirst().orElseThrow();
        assertThat(id.getSource()).isEqualTo(ParamSource.PATH);
        assertThat(id.getType()).isEqualTo("java.lang.Long");
        assertThat(id.isRequired()).isTrue();
        ParamInfo filter = params.stream().filter(p -> p.getName().equals("filter")).findFirst().orElseThrow();
        assertThat(filter.getSource()).isEqualTo(ParamSource.QUERY);
        assertThat(filter.isRequired()).isFalse();

        ParamInfo body = find(apis, "/api/users").getParams().get(0);
        assertThat(body.getSource()).isEqualTo(ParamSource.BODY);
    }

    @Test
    void scansGenericReturnTypeAndDeprecated() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        ApiInfo legacy = find(apis, "/api/users/legacy");
        assertThat(legacy.getReturnType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(legacy.isDeprecated()).isTrue();
    }

    @Test
    void cachesResult() {
        RestApiScanner scanner = scannerFor(SampleControllers.UserController.class);
        assertThat(scanner.scan()).isSameAs(scanner.scan());
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `mvn -q -Dtest=RestApiScannerTest test`
Expected: 编译失败 `cannot find symbol: method scan()`。

- [ ] **Step 4: 扩展 `RestApiScanner`（在 Task 4 文件基础上新增）**

将 `scanner/RestApiScanner.java` 全量替换为：
```java
package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final ArchimedesApiProperties properties;
    private final AtomicReference<List<ApiInfo>> cache = new AtomicReference<>();

    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        this.handlerMappings = handlerMappings;
        this.properties = properties;
    }

    /** 首次调用扫描并缓存；后续返回同一不可变列表。 */
    public List<ApiInfo> scan() {
        List<ApiInfo> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        cache.compareAndSet(null, doScan());
        return cache.get();
    }

    private List<ApiInfo> doScan() {
        List<ApiInfo> apis = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                try {
                    apis.add(toApiInfo(entry.getKey(), entry.getValue()));
                } catch (Exception ex) {
                    log.warn("Archimedes: failed to parse handler {}, skipped", entry.getValue(), ex);
                }
            }
        }
        apis.sort(Comparator
                .comparing((ApiInfo a) -> a.getPaths().isEmpty() ? "" : a.getPaths().get(0))
                .thenComparing(a -> a.getHttpMethods().isEmpty() ? "" : a.getHttpMethods().get(0)));
        return Collections.unmodifiableList(apis);
    }

    ApiInfo toApiInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        ApiInfo info = new ApiInfo();
        info.setControllerClass(handlerMethod.getBeanType().getName());
        info.setHandlerMethod(method.getName());
        info.setPaths(extractPaths(mappingInfo));
        info.setHttpMethods(mappingInfo.getMethodsCondition().getMethods().stream()
                .map(Enum::name).sorted().collect(Collectors.toList()));
        info.setParams(extractParams(handlerMethod));
        info.setReturnType(method.getGenericReturnType().getTypeName());
        info.setConsumes(mappingInfo.getConsumesCondition().getConsumableMediaTypes().stream()
                .map(Object::toString).sorted().collect(Collectors.toList()));
        info.setProduces(mappingInfo.getProducesCondition().getProducibleMediaTypes().stream()
                .map(Object::toString).sorted().collect(Collectors.toList()));
        info.setDeprecated(method.isAnnotationPresent(Deprecated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class));
        return info;
    }

    static List<String> extractPaths(RequestMappingInfo mappingInfo) {
        PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
        if (pathPatterns != null && !pathPatterns.getPatterns().isEmpty()) {
            return pathPatterns.getPatterns().stream()
                    .map(PathPattern::getPatternString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        PatternsRequestCondition patterns = mappingInfo.getPatternsCondition();
        if (patterns != null && !patterns.getPatterns().isEmpty()) {
            return patterns.getPatterns().stream()
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<ParamInfo> extractParams(HandlerMethod handlerMethod) {
        List<ParamInfo> params = new ArrayList<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            parameter.initParameterNameDiscovery(PARAM_NAME_DISCOVERER);
            params.add(toParamInfo(parameter));
        }
        return params;
    }

    private ParamInfo toParamInfo(MethodParameter parameter) {
        String type = parameter.getGenericParameterType().getTypeName();

        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = firstNonEmpty(requestParam.name(), requestParam.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.QUERY, type, requestParam.required());
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = firstNonEmpty(pathVariable.name(), pathVariable.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.PATH, type, pathVariable.required());
        }
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String name = firstNonEmpty(requestHeader.name(), requestHeader.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.HEADER, type, requestHeader.required());
        }
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (requestBody != null) {
            return new ParamInfo(fallbackName(parameter), ParamSource.BODY, type, requestBody.required());
        }
        return new ParamInfo(fallbackName(parameter), ParamSource.OTHER, type, false);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String fallbackName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -q -Dtest=RestApiScannerTest,PathExtractionTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java src/test/java/io/github/nianliu/archimedes/scanner/SampleControllers.java src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerTest.java
git commit -m "feat: scan RequestMappingHandlerMapping into ApiInfo with caching"
```

---

## Task 6: 扫描器——排除自身/错误端点 + 包过滤

**Files:**
- Modify: `src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java`（`doScan` 增加过滤；新增 `isExcluded`）
- Test: `src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerExclusionTest.java`

**Interfaces:**
- Consumes: `RestApiScanner.scan()`（Task 5）、`SampleControllers.buildMapping`（Task 5）、`ArchimedesApiProperties`（Task 3）。
- Produces: `scan()` 结果排除：路径等于/前缀为 `properties.getBasePath()` 的接口（即自身端点）、`/error` 与 `/error/**`、`controllerClass` 含 `BasicErrorController` 的；当 `basePackages` 非空，仅保留类名以其中之一为前缀者。

- [ ] **Step 1: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerExclusionTest.java`:
```java
package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiScannerExclusionTest {

    @RestController
    static class MixedController {
        @GetMapping("/error/custom")
        public String err() {
            return "";
        }

        @GetMapping("/archimedes/thing")
        public String underBasePath() {
            return "";
        }

        @GetMapping("/keep/me")
        public String keep() {
            return "";
        }
    }

    private List<String> scannedPaths(ArchimedesApiProperties props, Class<?> controller) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controller);
        return new RestApiScanner(List.of(mapping), props).scan().stream()
                .flatMap(a -> a.getPaths().stream())
                .toList();
    }

    @Test
    void excludesErrorAndOwnEndpoints() {
        List<String> paths = scannedPaths(new ArchimedesApiProperties(), MixedController.class);
        assertThat(paths).containsExactly("/keep/me");
    }

    @Test
    void basePackagesFilterExcludesNonMatching() {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePackages(List.of("com.nonexistent"));

        List<String> paths = scannedPaths(props, SampleControllers.UserController.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void basePackagesFilterKeepsMatching() {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePackages(List.of("io.github.nianliu"));

        List<ApiInfo> apis = new RestApiScanner(
                List.of(SampleControllers.buildMapping(SampleControllers.UserController.class)), props).scan();
        assertThat(apis).isNotEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=RestApiScannerExclusionTest test`
Expected: `excludesErrorAndOwnEndpoints` 失败（当前 `/error/custom`、`/archimedes/thing` 未被排除，实际含 3 条路径）。

- [ ] **Step 3: 修改 `doScan` 增加过滤，并新增 `isExcluded`**

在 `RestApiScanner` 中，将 `doScan()` 里的这段：
```java
                try {
                    apis.add(toApiInfo(entry.getKey(), entry.getValue()));
                } catch (Exception ex) {
```
改为：
```java
                try {
                    ApiInfo info = toApiInfo(entry.getKey(), entry.getValue());
                    if (!isExcluded(info)) {
                        apis.add(info);
                    }
                } catch (Exception ex) {
```

并在类中新增方法（放在 `toApiInfo` 之后）：
```java
    private boolean isExcluded(ApiInfo info) {
        String controllerClass = info.getControllerClass();
        if (controllerClass.contains("BasicErrorController")) {
            return true;
        }
        String basePath = properties.getBasePath();
        boolean underBasePath = info.getPaths().stream()
                .anyMatch(p -> p.equals(basePath) || p.startsWith(basePath + "/"));
        if (underBasePath) {
            return true;
        }
        boolean isError = info.getPaths().stream()
                .anyMatch(p -> p.equals("/error") || p.startsWith("/error/"));
        if (isError) {
            return true;
        }
        List<String> basePackages = properties.getBasePackages();
        if (basePackages != null && !basePackages.isEmpty()) {
            return basePackages.stream().noneMatch(controllerClass::startsWith);
        }
        return false;
    }
```

- [ ] **Step 4: 运行确认通过（含回归）**

Run: `mvn -q -Dtest=RestApiScannerExclusionTest,RestApiScannerTest,PathExtractionTest test`
Expected: `Tests run: 9, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/scanner/RestApiScanner.java src/test/java/io/github/nianliu/archimedes/scanner/RestApiScannerExclusionTest.java
git commit -m "feat: exclude self/error endpoints and apply base-package filter"
```

---

## Task 7: 控制器（JSON 端点 + UI 注入）+ 内置页面资源

**Files:**
- Create: `src/main/java/io/github/nianliu/archimedes/web/ArchimedesApiController.java`
- Create: `src/main/resources/archimedes-ui/index.html`
- Test: `src/test/java/io/github/nianliu/archimedes/web/ArchimedesApiControllerTest.java`

**Interfaces:**
- Consumes: `RestApiScanner.scan()`（Task 5）、`ArchimedesApiProperties`（Task 3）。
- Produces:
  - `ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties)`。
  - `@GetMapping("${archimedes.api.base-path:/archimedes}/apis")` → `List<ApiInfo> apis()`。
  - `@GetMapping("${archimedes.api.base-path:/archimedes}")` → `ResponseEntity<String> ui()`：`uiEnabled=false` 返回 404；否则读 `archimedes-ui/index.html`，把 `__ARCHIMEDES_API_URL__` 替换为 `basePath + "/apis"`，`text/html` 返回。

- [ ] **Step 1: 写内置页面资源**

`src/main/resources/archimedes-ui/index.html`:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Archimedes · API Explorer</title>
    <style>
        body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 24px; color: #222; }
        h1 { font-size: 20px; }
        #q { padding: 8px; width: 320px; font-size: 14px; margin-bottom: 12px; }
        table { border-collapse: collapse; width: 100%; font-size: 13px; }
        th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; vertical-align: top; }
        th { background: #f5f5f5; position: sticky; top: 0; }
        .m { font-weight: 600; }
        .dep { text-decoration: line-through; color: #999; }
        code { background: #f2f2f2; padding: 1px 4px; border-radius: 3px; }
        #count { color: #666; font-size: 12px; margin-left: 8px; }
    </style>
</head>
<body>
<h1>Archimedes · REST API Explorer<span id="count"></span></h1>
<input id="q" placeholder="filter by path / method / class..." oninput="render()">
<table>
    <thead>
    <tr><th>Method</th><th>Path</th><th>Handler</th><th>Params</th><th>Returns</th></tr>
    </thead>
    <tbody id="rows"></tbody>
</table>
<script>
    var API_URL = "__ARCHIMEDES_API_URL__";
    var data = [];

    function esc(s) {
        return String(s).replace(/[&<>]/g, function (c) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;'}[c];
        });
    }

    function paramText(p) {
        return p.map(function (x) { return x.name + ':' + x.source; }).join(', ');
    }

    function render() {
        var q = document.getElementById('q').value.toLowerCase();
        var rows = data.filter(function (a) {
            var hay = (a.httpMethods.join(' ') + ' ' + a.paths.join(' ') + ' ' + a.controllerClass).toLowerCase();
            return hay.indexOf(q) >= 0;
        });
        document.getElementById('count').textContent = ' (' + rows.length + '/' + data.length + ')';
        document.getElementById('rows').innerHTML = rows.map(function (a) {
            var cls = a.deprecated ? ' class="dep"' : '';
            return '<tr' + cls + '>'
                + '<td class="m">' + esc(a.httpMethods.join(', ')) + '</td>'
                + '<td>' + a.paths.map(function (p) { return '<code>' + esc(p) + '</code>'; }).join('<br>') + '</td>'
                + '<td>' + esc(a.controllerClass.split('.').pop()) + '#' + esc(a.handlerMethod) + '</td>'
                + '<td>' + esc(paramText(a.params)) + '</td>'
                + '<td><code>' + esc(a.returnType) + '</code></td>'
                + '</tr>';
        }).join('');
    }

    fetch(API_URL)
        .then(function (r) { return r.json(); })
        .then(function (j) { data = j; render(); })
        .catch(function (e) {
            document.getElementById('rows').innerHTML =
                '<tr><td colspan="5">Failed to load: ' + esc(e) + '</td></tr>';
        });
</script>
</body>
</html>
```

- [ ] **Step 2: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/web/ArchimedesApiControllerTest.java`:
```java
package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchimedesApiControllerTest {

    private final RestApiScanner scanner = mock(RestApiScanner.class);

    @Test
    void uiInjectsAbsoluteApiUrl() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        ResponseEntity<String> resp = controller.ui();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();
        assertThat(resp.getBody()).contains("/archimedes/apis");
        assertThat(resp.getBody()).doesNotContain("__ARCHIMEDES_API_URL__");
    }

    @Test
    void uiUsesConfiguredBasePath() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePath("/custom");
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        assertThat(controller.ui().getBody()).contains("/custom/apis");
    }

    @Test
    void uiReturns404WhenDisabled() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setUiEnabled(false);
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        assertThat(controller.ui().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `mvn -q -Dtest=ArchimedesApiControllerTest test`
Expected: 编译失败 `cannot find symbol: class ArchimedesApiController`。

- [ ] **Step 4: 写实现**

`web/ArchimedesApiController.java`:
```java
package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class ArchimedesApiController {

    private static final String UI_RESOURCE = "archimedes-ui/index.html";
    private static final String API_URL_PLACEHOLDER = "__ARCHIMEDES_API_URL__";

    private final RestApiScanner scanner;
    private final ArchimedesApiProperties properties;
    private final AtomicReference<String> renderedUi = new AtomicReference<>();

    public ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties) {
        this.scanner = scanner;
        this.properties = properties;
    }

    @GetMapping(value = "${archimedes.api.base-path:/archimedes}/apis", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiInfo> apis() {
        return scanner.scan();
    }

    @GetMapping(value = "${archimedes.api.base-path:/archimedes}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ui() throws IOException {
        if (!properties.isUiEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderUi());
    }

    private String renderUi() throws IOException {
        String cached = renderedUi.get();
        if (cached != null) {
            return cached;
        }
        String template;
        try (InputStream in = new ClassPathResource(UI_RESOURCE).getInputStream()) {
            template = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        String rendered = template.replace(API_URL_PLACEHOLDER, properties.getBasePath() + "/apis");
        renderedUi.compareAndSet(null, rendered);
        return renderedUi.get();
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -q -Dtest=ArchimedesApiControllerTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/web/ArchimedesApiController.java src/main/resources/archimedes-ui/index.html src/test/java/io/github/nianliu/archimedes/web/ArchimedesApiControllerTest.java
git commit -m "feat: add controller exposing apis JSON and injected UI page"
```

---

## Task 8: 自动装配 + imports 注册

**Files:**
- Create: `src/main/java/io/github/nianliu/archimedes/config/ArchimedesAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `src/test/java/io/github/nianliu/archimedes/config/ArchimedesAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `RestApiScanner`（Task 5）、`ArchimedesApiController`（Task 7）、`ArchimedesApiProperties`（Task 3）。
- Produces: `ArchimedesAutoConfiguration`：`@AutoConfiguration(afterName=WebMvcAutoConfiguration)` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass(RequestMappingHandlerMapping)` + `@ConditionalOnProperty("archimedes.api.enabled", matchIfMissing=true)` + `@EnableConfigurationProperties(ArchimedesApiProperties.class)`；两个 `@Bean`：`archimedesRestApiScanner`、`archimedesApiController`。

- [ ] **Step 1: 写失败测试**

`src/test/java/io/github/nianliu/archimedes/config/ArchimedesAutoConfigurationTest.java`:
```java
package io.github.nianliu.archimedes.config;

import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesAutoConfigurationTest {

    @Test
    void registersBeansInServletWebApp() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RestApiScanner.class);
                    assertThat(context).hasSingleBean(ArchimedesApiController.class);
                    assertThat(context).hasSingleBean(ArchimedesApiProperties.class);
                });
    }

    @Test
    void skipsInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RestApiScanner.class));
    }

    @Test
    void skipsWhenDisabled() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .withPropertyValues("archimedes.api.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RestApiScanner.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=ArchimedesAutoConfigurationTest test`
Expected: 编译失败 `cannot find symbol: class ArchimedesAutoConfiguration`。

- [ ] **Step 3: 写实现**

`config/ArchimedesAutoConfiguration.java`:
```java
package io.github.nianliu.archimedes.config;

import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ArchimedesApiProperties.class)
public class ArchimedesAutoConfiguration {

    @Bean
    public RestApiScanner archimedesRestApiScanner(List<RequestMappingHandlerMapping> handlerMappings,
                                                   ArchimedesApiProperties properties) {
        return new RestApiScanner(handlerMappings, properties);
    }

    @Bean
    public ArchimedesApiController archimedesApiController(RestApiScanner scanner,
                                                           ArchimedesApiProperties properties) {
        return new ArchimedesApiController(scanner, properties);
    }
}
```

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.github.nianliu.archimedes.config.ArchimedesAutoConfiguration
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -Dtest=ArchimedesAutoConfigurationTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/nianliu/archimedes/config/ArchimedesAutoConfiguration.java "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports" src/test/java/io/github/nianliu/archimedes/config/ArchimedesAutoConfigurationTest.java
git commit -m "feat: add auto-configuration and imports registration"
```

---

## Task 9: 端到端集成测试（引入即用验证）

**Files:**
- Test: `src/test/java/io/github/nianliu/archimedes/EndToEndTest.java`

**Interfaces:**
- Consumes: 全部生产代码 + `AutoConfiguration.imports`（Task 1-8）。
- Produces: 一个 `@SpringBootTest(RANDOM_PORT)` 用例，用 `@EnableAutoConfiguration` 触发 imports 装配，验证真实 HTTP 行为。

- [ ] **Step 1: 写端到端测试**

`src/test/java/io/github/nianliu/archimedes/EndToEndTest.java`:
```java
package io.github.nianliu.archimedes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesRestApisAndExcludesFrameworkEndpoints() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/apis", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        List<Map<String, Object>> apis = mapper.readValue(resp.getBody(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        // 用户接口在列
        assertThat(apis).anySatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/demo/hello"));
        // 排除框架 /error 与自身 /archimedes/**
        assertThat(apis).noneSatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/error"));
        assertThat(apis).noneSatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/archimedes/apis"));
    }

    @Test
    void demoEndpointParamIsScanned() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/apis", String.class);
        List<Map<String, Object>> apis = mapper.readValue(resp.getBody(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        Map<String, Object> hello = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/demo/hello"))
                .findFirst().orElseThrow();
        assertThat((List<String>) hello.get("httpMethods")).contains("GET");
        List<Map<String, Object>> params = (List<Map<String, Object>>) hello.get("params");
        assertThat(params).anySatisfy(p -> {
            assertThat(p.get("name")).isEqualTo("name");
            assertThat(p.get("source")).isEqualTo("QUERY");
            assertThat(p.get("required")).isEqualTo(true);
        });
    }

    @Test
    void servesUiWithInjectedApiUrl() {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("/archimedes/apis");
        assertThat(resp.getBody()).doesNotContain("__ARCHIMEDES_API_URL__");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
        @Bean
        DemoController demoController() {
            return new DemoController();
        }
    }

    @RestController
    static class DemoController {
        @GetMapping("/demo/hello")
        public String hello(@RequestParam String name) {
            return "hi " + name;
        }
    }
}
```

- [ ] **Step 2: 运行确认通过**

Run: `mvn -q -Dtest=EndToEndTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。若 `demoEndpointParamIsScanned` 因参数名不是 `name` 而失败，检查 pom 的 `maven-compiler-plugin` 是否已开启 `<parameters>true</parameters>`（Task 1 Step 2）。

- [ ] **Step 3: 全量回归**

Run: `mvn -q test`
Expected: 全部测试通过（约 21 条），`BUILD SUCCESS`。

- [ ] **Step 4: 提交**

```bash
git add src/test/java/io/github/nianliu/archimedes/EndToEndTest.java
git commit -m "test: add end-to-end import-and-use integration test"
```

---

## 完成后手动验收（可选）

在一个真实的 Boot 3.x 项目里 `mvn install` 本库并引入，启动后访问：
- `GET /archimedes/apis` → 返回接口 JSON 数组。
- 浏览器打开 `/archimedes` → 可搜索的接口表格。

## 本切完成即达成
「Boot 3.x 项目引入依赖 → 零配置得到 REST 接口清单（JSON + 网页）」，且扫描逻辑已为 Boot 2.x 就绪（`extractPaths` 双路径 + 仅用跨版本稳定 API），2.x 真机认证与多版本 CI 留待后续切片。
