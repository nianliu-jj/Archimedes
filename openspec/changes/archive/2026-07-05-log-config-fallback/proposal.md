# Proposal: log-config-fallback

## Why

需求：宿主项目 resources 下没有 logback-spring.xml 时，使用本依赖内置的 logback 配置（pattern 含 traceId，含滚动文件输出）；日志格式（pattern/路径/保留天数/单文件大小）可通过 `archimedes.log.*` 自定义（`docs/项目需求.md` §四）。当前宿主若无自有配置，Boot 默认 pattern 不含 traceId——链路信息在控制台/文件里不可见。

## What Changes

- core 新增内置配置 `classpath:archimedes-logback.xml`（**刻意不叫 logback-spring.xml**，避免被宿主的 logback 自动发现机制误拾取）：
  - CONSOLE + 滚动 FILE（SizeAndTimeBasedRollingPolicy）双 appender
  - pattern 默认含 `[%X{traceId:-}]` `[%X{spanId:-}]`
  - 通过 `<springProperty source="archimedes.log.*">` 读取用户自定义：`pattern` / `path`（默认 ./logs）/ `max-history`（默认 30）/ `max-file-size`（默认 100MB）
- core 新增 `ArchimedesLoggingEnvironmentPostProcessor`：在日志系统初始化前检测——用户已设 `logging.config`、或 classpath 存在 `logback-spring.xml`/`logback.xml`、或日志实现非 logback，则不介入；否则注入 `logging.config=classpath:archimedes-logback.xml`
- core 自带 `META-INF/spring.factories` 注册 EPP（`EnvironmentPostProcessor` key 在 SB2/SB3 均有效，**两个 starter 零重复**）
- 时机依据：`EnvironmentPostProcessorApplicationListener`（HIGHEST+10）先于 `LoggingApplicationListener`（HIGHEST+20）执行
- 测试策略：sb3 模块无用户配置（断言兜底生效分支），sb2 模块 test resources 放一份 `logback-spring.xml`（断言用户优先分支）——两分支跨模块全覆盖
- `.gitignore` 增加 `logs/`；starter 测试用 `application.properties` 把日志路径导向 `target/`

## Capabilities

### New Capabilities

- `log-config-fallback`: 内置 logback 配置的兜底注入（用户无配置时）、用户配置优先规则、`archimedes.log.*` 格式配置面。

### Modified Capabilities

（无）

## Impact

- **core**：新增 EPP 类 + xml 资源 + spring.factories；无新依赖
- **starter×2**：无代码改动（EPP 随 core jar 生效）；各加测试
- **行为影响**：无自有 logback 配置的宿主，控制台 pattern 变为含 traceId 的 Archimedes 默认，并新增 `./logs` 滚动文件输出（需求明确要求，README 说明关闭方式=提供自己的 logback 配置或设 `logging.config`）
- **采集链路（Slice 6）不受影响**：Appender 结构化采集与输出格式解耦
