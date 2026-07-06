# Design: multi-module-split

## Context

当前是单模块 `io.github.nianliu:archimedes`（SB 3.3.5，JDK 21），包含模型（`ApiInfo`/`ParamInfo`/`ParamSource`）、`RestApiScanner`、`ArchimedesApiController`、`ArchimedesAutoConfiguration`、UI 静态资源与全套测试。

代码考古结论（决定性约束）：**现有 main 代码零 servlet API 导入**，全部依赖 `org.springframework.*` 与 JDK——这些包名在 Spring 5.3（SB 2.7）与 Spring 6（SB 3.x）完全一致，且用到的 API（`RequestMappingInfo`、`PathPatternsRequestCondition`、`RequestMappingHandlerMapping` 等）在 5.3 中均已存在。javax/jakarta 分叉尚未发生——但下一个 slice（TraceIdFilter）必然引入 `Filter`/`HttpServletRequest`，分叉不可避免。

## Goals / Non-Goals

**Goals:**
- 多模块结构：`archimedes-parent`（聚合/父 POM）、`archimedes-core`、`archimedes-spring-boot-2-starter`、`archimedes-spring-boot-3-starter`
- 现有全部功能在 SB 2.7.x 与 SB 3.3.x 应用中行为一致，引入对应 starter 即用
- 为后续 slice 预留形状：servlet 相关实现落 starter，框架无关逻辑落 core

**Non-Goals:**
- 不新增任何功能（WebSocket/trace/日志均属后续 slice）
- 不发布到中央仓库（发布流程另行处理）
- 不支持 SB 2.7 以下版本

## Decisions

### D1：core 按"最低公分母"编译——SB 2.7 BOM + `--release 8`，单份 jar 双端复用

**选型**：core 的 `dependencyManagement` 导入 SB 2.7.18 BOM（Spring 5.3.x，provided/optional 语义），字节码目标 `--release 8`；两个 starter 均依赖同一个 core jar。

**理由**：现有代码只触及 Spring 5.3/6 二进制兼容的 API 面，单份编译即可运行于两端，避免任何代码复制。字节码降到 8 是因为 SB 2.7 官方支持 Java 8+，core 不能限制宿主 JDK。

**备选及否决**：
- *core 双份编译（maven classifier 或双模块）*：解决的是尚不存在的问题，复杂度先付；等某个 API 真分叉时再局部处理。
- *core 完全去 Spring 化（纯模型 + SPI）、扫描器下沉 starter*：扫描器逻辑会在两个 starter 里复制两份，违背 DRY，且当前扫描器并不触碰分叉 API。

**护栏**：core 禁止导入 `javax.servlet.*`/`jakarta.servlet.*`（写入模块 POM 注释 + 后续可加 enforcer 规则）；一旦某功能需要 servlet API，它属于 starter。

### D2：BOM 按模块隔离，父 POM 不统一导入 Spring Boot BOM

父 POM 只管插件与公共属性；core 与 sb2-starter 各自导入 SB 2.7.18 BOM，sb3-starter 导入 SB 3.3.5 BOM。否则两套 BOM 在父级打架。

### D3：自动装配 = "core 持有装配逻辑素材，starter 持薄注册层"

`ArchimedesAutoConfiguration`（含 `@AutoConfiguration`、条件注解）在两个 starter 中各有一个类（包名区分，如 `...archimedes.boot2.` / `...archimedes.boot3.`），内容为纯 Bean 定义薄层，均委托 core 的 scanner/controller/properties。注册方式：
- sb2-starter：`META-INF/spring.factories`（`EnableAutoConfiguration` key）
- sb3-starter：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

`@AutoConfiguration` 注解 SB 2.7 已存在，两边可用同名写法。`ArchimedesApiProperties`（`@ConfigurationProperties`）留在 core，注解两端同包名，由 starter 的 `@EnableConfigurationProperties` 激活。

### D4：字节码目标——core/sb2-starter `--release 8`，sb3-starter `--release 17`

工具链仍是 JDK 21（现有环境），用 `maven.compiler.release` 分模块控制。sb3 跟随 SB3 的 Java 17 底线。

### D5：UI 静态资源随 core 打包

`archimedes-ui/` 资源目录留在 core 的 `src/main/resources`，controller（也在 core）从 classpath 读取——两 starter 天然共享，无需独立 UI 模块。**否决**独立 `archimedes-ui` 模块：目前只有静态文件，一个目录足矣，模块化收益为零。

### D6：根 POM 变聚合 POM，artifactId 改为 `archimedes-parent`

现有 `io.github.nianliu:archimedes` 坐标停用（BREAKING，项目尚未发布，无真实迁移成本）。版本延续 `1.0-SNAPSHOT`。

## Risks / Trade-offs

- [Spring 5.3 官方仅支持到 Java 19，SB2 starter 测试将在 JDK 21 上跑] → 现有代码不用 CGLIB 代理复杂场景，2.7.18 + 简单上下文在 21 上实测普遍可行；若 ByteBuddy/CGLIB 报错，测试 JVM 加 `-Dnet.bytebuddy.experimental=true` 或将该测试收窄为不启完整上下文的切片测试。
- [core 依赖"Spring 5.3/6 API 面兼容"这一隐式契约，未来改 core 可能悄悄引入仅 Spring 6 的 API] → sb2-starter 的集成测试在 SB 2.7 依赖树下编译运行，任何不兼容会在 CI 即刻爆掉（这正是双端集成测试的核心价值）。
- [`--release 8` 限制 core 不能用 records/新语法] → 现有代码未用；后续想用新语法的代码天然属于 sb3 侧或等 SB2 支持退役。
- [多模块后 IDE/构建复杂度上升] → 标准 Maven reactor 结构，`mvn` 根目录一键构建不变。

## Migration Plan

1. 根 POM 改聚合；新增四个模块 POM
2. main 代码与资源整体迁入 core（包名不动）；自动装配类在两 starter 各建薄层
3. 测试迁移：模型/扫描器单测随 core；端到端集成测试在两 starter 各一套（SB2 侧新写，SB3 侧迁移现有）
4. 全量 `mvn clean verify` 通过即完成；回滚 = revert 该分支（无外部消费者）

## Open Questions

（无——关键决策已在 explore 阶段与用户确认：SB2 基线 2.7.x；本期不做 javaagent；UI 不独立模块。）
