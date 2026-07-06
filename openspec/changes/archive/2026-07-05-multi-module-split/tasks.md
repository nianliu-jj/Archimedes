# Tasks: multi-module-split

## 1. 骨架搭建

- [x] 1.1 根 `pom.xml` 改为聚合/父 POM：artifactId 改为 `archimedes-parent`、packaging=pom、声明四个 module、仅保留插件管理与公共属性（不导入任何 Spring Boot BOM）
- [x] 1.2 创建 `archimedes-core/pom.xml`：导入 SB 2.7.18 BOM（dependencyManagement）、依赖 spring-web/spring-webmvc/spring-boot（provided 或 compile 语义按现状）、`maven.compiler.release=8`
- [x] 1.3 创建 `archimedes-spring-boot-2-starter/pom.xml`：依赖 core、导入 SB 2.7.18 BOM、`release=8`、测试依赖 spring-boot-starter-web/test（2.7.18）
- [x] 1.4 创建 `archimedes-spring-boot-3-starter/pom.xml`：依赖 core、导入 SB 3.3.5 BOM、`release=17`、测试依赖 spring-boot-starter-web/test（3.3.5）
- [x] 1.5 空模块状态下 `mvn clean install` 通过（reactor 顺序正确）——实际以全量构建一并验证

## 2. core 迁移

- [x] 2.1 迁移 `model/`（ApiInfo、ParamInfo、ParamSource）、`scanner/RestApiScanner`、`web/ArchimedesApiController`、`config/ArchimedesApiProperties` 到 core（包名不变）；`archimedes-ui/` 资源迁入 core resources
- [x] 2.2 确认 core 源码在 `--release 8` + Spring 5.3 下编译通过（主代码为纯 Java 8 语法；测试代码含 `List.of`/`.toList()`，以 `testRelease=17` 编译，不随 jar 发布）
- [x] 2.3 迁移模型/扫描器/控制器的单元测试到 core（含 ArchimedesApiPropertiesTest，其仅依赖 ApplicationContextRunner，可在 2.7 树下运行），core `mvn test` 通过（16 个测试全绿）

## 3. starter 薄层

- [x] 3.1 sb3-starter：新建 `boot3` 包下的 `ArchimedesAutoConfiguration`（内容取自现单模块版本，委托 core 类），注册 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [x] 3.2 sb2-starter：新建 `boot2` 包下的 `ArchimedesAutoConfiguration` 薄层（经典 `@Configuration(proxyBeanMethods=false)` + `@AutoConfigureAfter`，兼容 2.7 全系），注册 `META-INF/spring.factories`（EnableAutoConfiguration key）
- [x] 3.3 删除原单模块 `src/`（用户重构时已移出）与过渡目录 `archimedes/`，确认根目录无残留源码
- [x] 3.4 （会话中新增）`example` 模块接入新结构：依赖改为 `archimedes-spring-boot-3-starter`、自带 SB 3.3.5 BOM、release=17

## 4. 双端集成测试

- [x] 4.1 sb3-starter：迁移现有端到端 import-and-use 集成测试与自动装配测试（含 enabled=false 关闭场景、base-path、UI 页面断言），`mvn verify` 通过（6 个测试全绿）
- [x] 4.2 sb2-starter：新写等价的端到端集成测试（同一组样例 controller：零配置装配、/archimedes/apis JSON 语义、UI 页面、enabled=false 关闭），在 SB 2.7.18 依赖树 + JDK 21 下通过（6 个测试全绿，Tomcat 9.0.83，未触发 CGLIB 兼容问题）
- [x] 4.3 断言双端扫描语义一致：SB2 与 SB3 集成测试使用相同的样例 controller 定义与相同的期望 JSON 结构（boot2/EndToEndTest 与 boot3/EndToEndTest 互为镜像）

## 5. 收尾验证

- [x] 5.1 根目录 `mvn clean verify` 全绿（28 测试：core 16 + sb2 6 + sb3 6）；core 产物 class 版本 52（`ca fe ba be 00 00 00 34`）、jar 内含 archimedes-ui 资源、core 无 servlet 导入（grep 验证）；依赖树隔离验证：sb2→Spring 5.3.31/Boot 2.7.18，sb3→Spring 6.1.14/Boot 3.3.5；example 应用真机启动并验证 /archimedes/apis 与 UI
- [x] 5.2 更新 README 的引入方式（两个 starter 坐标 + 版本矩阵说明）
