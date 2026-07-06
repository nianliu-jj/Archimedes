# Proposal: multi-module-split

## Why

Archimedes 的最终蓝图（接口契约展示 REST/WS/Dubbo/gRPC/SOFARPC-TR/tRPC + 全链路日志追踪 + 日志采集查询）要求同时支持 Spring Boot 2.7.x（javax）与 3.x（jakarta）。两套 Servlet API 包路径不兼容，单模块无法同时编译；且后续 trace/日志/RPC 各 slice 若继续在单模块上堆叠，拆分成本会越来越高。现在拆，是后续所有 slice 的地基。

## What Changes

- **BREAKING**：单模块 `io.github.nianliu:archimedes` 拆为 Maven 多模块工程，原坐标不再发布：
  - `archimedes-parent`：父 POM，统一依赖与插件管理
  - `archimedes-core`：无 Servlet 依赖的核心（`ApiInfo`/`ParamInfo`/`ParamSource` 模型、扫描器逻辑、UI 静态资源）
  - `archimedes-spring-boot-2-starter`：javax 世界，SB 2.7.x 基线，`META-INF/spring.factories` 注册自动装配
  - `archimedes-spring-boot-3-starter`：jakarta 世界，SB 3.3.x，`META-INF/spring/...AutoConfiguration.imports` 注册
- 现有全部功能（REST 接口扫描、`/archimedes` JSON 端点 + 注入式 UI 页、base-package 过滤、自动装配）在两个 starter 下行为一致。
- 现有测试迁移到对应模块；两个 starter 各自具备端到端"引入即用"集成测试。
- 用户侧用法不变：引入对应版本的 starter 即生效，零配置。

## Capabilities

### New Capabilities

- `modular-artifacts`: 多模块工件结构——core 不携带 Servlet/Web 依赖，starter 依赖 core 并各自绑定 SB 大版本；模块间依赖形状与发布坐标。
- `spring-boot-2-support`: 在 Spring Boot 2.7.x（javax）应用中引入 `archimedes-spring-boot-2-starter` 即获得与现有单模块等价的全部能力（扫描 + 端点 + UI + 配置属性）。
- `spring-boot-3-support`: 在 Spring Boot 3.x（jakarta）应用中引入 `archimedes-spring-boot-3-starter` 即获得同等能力。

### Modified Capabilities

（无——`openspec/specs/` 为空，无既有主 spec 需要修改。）

## Impact

- **构建**：根 `pom.xml` 变为聚合 POM；新增 4 个子模块 POM；CI/本地构建命令不变（`mvn` 于根目录）。
- **代码**：`src/main/java` 现有类按归属迁往 `archimedes-core` 与两个 starter；自动装配类需按 javax/jakarta 各写一份（或核心逻辑下沉 core、starter 仅做薄注册层）。
- **资源**：`archimedes-ui` 静态资源迁入 core，随 core 打包，两 starter 共享。
- **测试**：现有测试按模块迁移；SB2 starter 的测试需在 2.7.x 依赖树下运行（父 POM 中按模块管理不同 BOM）。
- **后续 slice**：trace 核心、LogStore SPI、logback fallback、RPC 扫描器都将落在 core + 条件装配的形态上，依赖本次拆分。
