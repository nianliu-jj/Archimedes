# Spec Delta: log-config-fallback

## ADDED Requirements

### Requirement: 无用户日志配置时使用内置配置
宿主未显式设置 `logging.config` 且 classpath 不存在任何 logback 约定配置文件（`logback-spring.xml`/`logback.xml`/`logback-spring.groovy`/`logback.groovy`/`logback-test.xml`）、且日志实现为 logback 时，系统 SHALL 在日志系统初始化前注入 `logging.config=classpath:archimedes-logback.xml`，使宿主获得含 traceId/spanId 的控制台输出与滚动文件输出。

#### Scenario: 零配置宿主获得内置日志配置
- **WHEN** 宿主没有任何 logback 配置文件并启动
- **THEN** 控制台日志 pattern 含 `[traceId]`，且滚动文件输出生效

#### Scenario: 可通过开关禁用兜底
- **WHEN** 配置 `archimedes.log.fallback-enabled=false`
- **THEN** 不注入 `logging.config`，宿主日志行为与未引入 Archimedes 时一致

### Requirement: 用户自有日志配置绝对优先
宿主已设置 `logging.config`、或 classpath 存在任一 logback 约定配置文件、或日志实现非 logback 时，系统 SHALL NOT 注入任何日志配置属性，宿主日志行为 SHALL 与未引入 Archimedes 时一致。

#### Scenario: 宿主自有 logback-spring.xml 优先
- **WHEN** 宿主 resources 下存在 logback-spring.xml 并启动
- **THEN** 使用宿主配置，`logging.config` 未被 Archimedes 注入

### Requirement: 日志格式可配置
内置配置 SHALL 通过 `archimedes.log.*` 支持自定义：`pattern`（日志格式模板）、`path`（文件目录，默认 ./logs）、`max-history`（保留天数，默认 30）、`max-file-size`（单文件大小，默认 100MB）。

#### Scenario: 自定义 pattern 生效
- **WHEN** 宿主配置 `archimedes.log.pattern` 为自定义模板并启动（处于兜底生效状态）
- **THEN** 控制台与文件输出按该模板渲染

#### Scenario: 自定义格式不影响链路查询
- **WHEN** 修改 `archimedes.log.pattern` 后按 traceId 查询日志
- **THEN** 查询结果与格式修改前语义一致（采集为结构化，与格式解耦）
