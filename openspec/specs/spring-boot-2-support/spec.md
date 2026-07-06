# spring-boot-2-support Specification

## Purpose

定义 `archimedes-spring-boot-2-starter` 在 Spring Boot 2.7.x（javax/Servlet 环境）下的引入即用能力：零配置自动装配、spring.factories 注册方式与属性开关。

## Requirements

### Requirement: SB2 应用引入即用
Spring Boot 2.7.x（javax/Servlet 环境）的 Web 应用引入 `archimedes-spring-boot-2-starter` 后，SHALL 无需任何配置即自动装配 Archimedes 全部现有能力：REST 接口扫描、`{base-path}/apis` JSON 端点、`{base-path}` UI 页面、`archimedes.api.*` 配置属性。

#### Scenario: 零配置自动装配
- **WHEN** 一个 SB 2.7.x Web 应用仅在 pom 中加入 `archimedes-spring-boot-2-starter` 依赖并启动
- **THEN** 应用上下文中存在 RestApiScanner、ArchimedesApiController 及属性 Bean，`/archimedes/apis` 返回该应用的接口 JSON，`/archimedes` 返回 UI 页面

#### Scenario: 扫描结果与 SB3 行为一致
- **WHEN** 同一组 controller 定义分别运行于 SB2（经 sb2-starter）与 SB3（经 sb3-starter）
- **THEN** `{base-path}/apis` 返回的接口元数据（路径、HTTP 方法、参数、返回类型、自身端点排除、base-package 过滤）语义一致

### Requirement: 通过 spring.factories 注册自动装配
sb2-starter SHALL 通过 `META-INF/spring.factories` 的 `org.springframework.boot.autoconfigure.EnableAutoConfiguration` key 注册其自动装配类。

#### Scenario: spring.factories 生效
- **WHEN** SB 2.7.x 应用启动且未显式 `@Import` 任何 Archimedes 类
- **THEN** 自动装配类被 Spring Boot 自动加载

### Requirement: 可通过属性关闭
sb2-starter SHALL 支持与现有单模块一致的开关属性：`archimedes.api.enabled=false` 时不注册任何 Archimedes Bean。

#### Scenario: 显式关闭
- **WHEN** SB2 应用配置 `archimedes.api.enabled=false` 并启动
- **THEN** 上下文中不存在 Archimedes 的任何 Bean，`/archimedes/apis` 返回 404
