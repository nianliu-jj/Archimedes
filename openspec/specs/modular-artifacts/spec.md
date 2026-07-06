# modular-artifacts Specification

## Purpose

定义 Archimedes 的 Maven 多模块工件结构：core 与两个 Spring Boot starter 的模块划分、依赖隔离与打包边界，使同一核心能力可分别服务于 Spring Boot 2 与 Spring Boot 3 应用。

## Requirements

### Requirement: Maven 多模块工件结构
项目 SHALL 以 Maven 多模块工程组织，包含 `archimedes-parent`（聚合/父 POM）、`archimedes-core`、`archimedes-spring-boot-2-starter`、`archimedes-spring-boot-3-starter` 四个模块，groupId 均为 `io.github.nianliu`，版本统一由父 POM 管理。

#### Scenario: 根目录一键构建
- **WHEN** 在仓库根目录执行 `mvn clean verify`
- **THEN** Maven reactor 按依赖顺序构建全部四个模块且全部测试通过

#### Scenario: starter 依赖 core
- **WHEN** 检查两个 starter 模块的依赖树
- **THEN** 二者均依赖同一个 `archimedes-core` 工件，且互不依赖对方

### Requirement: core 模块不携带 Servlet API 依赖
`archimedes-core` SHALL 不依赖（compile 与 runtime scope）任何 `javax.servlet` 或 `jakarta.servlet` API，其源码 SHALL 不导入这两个命名空间下的任何类型；core 的字节码目标 SHALL 为 Java 8。

#### Scenario: core 源码无 servlet 导入
- **WHEN** 在 core 模块的 main 源码中搜索 `import javax.servlet` 与 `import jakarta.servlet`
- **THEN** 无任何匹配

#### Scenario: core 字节码可运行于 Java 8+
- **WHEN** 检查 core 编译产物的 class 文件版本
- **THEN** 主版本号为 52（Java 8）

### Requirement: Spring Boot BOM 按模块隔离
父 POM SHALL NOT 在 `dependencyManagement` 中导入任何 Spring Boot BOM；`archimedes-core` 与 `archimedes-spring-boot-2-starter` SHALL 导入 Spring Boot 2.7.x BOM，`archimedes-spring-boot-3-starter` SHALL 导入 Spring Boot 3.3.x BOM。

#### Scenario: 各模块解析到正确的 Spring 版本
- **WHEN** 分别检查 sb2-starter 与 sb3-starter 的依赖树
- **THEN** sb2-starter 解析到 Spring Framework 5.3.x / Spring Boot 2.7.x，sb3-starter 解析到 Spring Framework 6.x / Spring Boot 3.3.x

### Requirement: UI 静态资源随 core 打包
UI 静态资源 SHALL 位于 `archimedes-core` 的 classpath 资源目录（`archimedes-ui/`）中，由 core 中的 controller 读取，两个 starter 无需各自携带 UI 资源。

#### Scenario: UI 资源存在于 core jar
- **WHEN** 检查 `archimedes-core` 的打包产物
- **THEN** 包含 `archimedes-ui/` 下的 UI 页面资源
