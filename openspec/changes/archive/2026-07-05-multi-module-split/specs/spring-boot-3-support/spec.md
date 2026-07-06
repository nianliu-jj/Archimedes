# Spec Delta: spring-boot-3-support

## ADDED Requirements

### Requirement: SB3 应用引入即用
Spring Boot 3.x（jakarta 环境）的 Web 应用引入 `archimedes-spring-boot-3-starter` 后，SHALL 无需任何配置即自动装配 Archimedes 全部现有能力：REST 接口扫描、`{base-path}/apis` JSON 端点、`{base-path}` UI 页面、`archimedes.api.*` 配置属性——与拆分前单模块行为完全等价。

#### Scenario: 零配置自动装配
- **WHEN** 一个 SB 3.3.x Web 应用仅在 pom 中加入 `archimedes-spring-boot-3-starter` 依赖并启动
- **THEN** 应用上下文中存在 RestApiScanner、ArchimedesApiController 及属性 Bean，`/archimedes/apis` 返回该应用的接口 JSON，`/archimedes` 返回 UI 页面

#### Scenario: 既有测试全部迁移通过
- **WHEN** 拆分前的全部测试（模型、扫描器、控制器、自动装配、端到端集成）迁移至 core 与 sb3-starter 后执行
- **THEN** 全部通过，无行为回归

### Requirement: 通过 AutoConfiguration.imports 注册自动装配
sb3-starter SHALL 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册其自动装配类。

#### Scenario: imports 文件生效
- **WHEN** SB 3.x 应用启动且未显式 `@Import` 任何 Archimedes 类
- **THEN** 自动装配类被 Spring Boot 自动加载

### Requirement: 可通过属性关闭
sb3-starter SHALL 支持与现有单模块一致的开关属性：`archimedes.api.enabled=false` 时不注册任何 Archimedes Bean。

#### Scenario: 显式关闭
- **WHEN** SB3 应用配置 `archimedes.api.enabled=false` 并启动
- **THEN** 上下文中不存在 Archimedes 的任何 Bean，`/archimedes/apis` 返回 404
