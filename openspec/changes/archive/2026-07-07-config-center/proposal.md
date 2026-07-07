## Why

`docs/需求.md` §3 要求的「配置信息展示与热更新」尚未实现：宿主应用无法在 Archimedes 控制台查看当前生效的全部配置（application.properties/yaml、环境变量、命令行参数等），也无法在不重启的情况下调整配置。作为 1.1 版本两大缺口能力之一，本变更补齐配置中心。

## What Changes

- 新增配置查询端点 `GET {base-path}/config`：遍历 `ConfigurableEnvironment` 的全部 `EnumerablePropertySource`，返回每项配置的 key / value / 来源属性源名称；敏感键（password/secret/token/credential/key 等）值脱敏展示。
- 新增热更新端点 `POST {base-path}/config/update`：接收 `{key, value}`，写入 Archimedes 动态属性源（`MapPropertySource`，位于 Environment 最高优先级）；支持删除覆盖（value 为 null 时移除动态键，恢复原值）。
- 热更新生效机制：更新后对 prefix 命中的 `@ConfigurationProperties` Bean 执行重绑定（Binder 重新 bind 到既有实例）；发布 Archimedes 自定义事件；若 classpath 存在 Spring Cloud 的 `EnvironmentChangeEvent` 则反射构造并同步发布（`@RefreshScope` 生态自动联动）。
- 新增配置项 `archimedes.config.*`：`enabled`（默认 true）、`hot-refresh-enabled`（默认 true，关闭后 update 端点拒绝写入）、`sensitive-keys`（脱敏关键字列表，可追加）。
- 内置 UI 新增「配置中心」Tab：全量配置表格（key/value/来源）、文本搜索过滤、行内编辑 + 应用按钮调用更新端点、动态覆盖项高亮标识。
- SB2/SB3 双 starter 同步装配（SERVLET 与 REACTIVE 分支均注册，控制器为纯注解式零 servlet 依赖）。

## Capabilities

### New Capabilities

- `config-management`: 配置信息展示（全属性源枚举 + 来源标注 + 敏感值脱敏）与运行时热更新（动态属性源 + @ConfigurationProperties 重绑定 + 事件发布 + UI 配置中心 Tab）。

### Modified Capabilities

<!-- 无：现有 spec 的需求不变，UI 新 Tab 归属 config-management 能力自身 -->

## Impact

- `archimedes-core`：新增 `io.github.nianliu.archimedes.env` 包（属性枚举服务、动态属性源管理器、重绑定器、配置属性类）与 `web/ArchimedesConfigController`；`archimedes-ui/index.html` 新增 Tab。
- `archimedes-spring-boot-2-starter` / `archimedes-spring-boot-3-starter`：SERVLET/REACTIVE 自动装配注册上述 Bean。
- 无新增第三方依赖（Spring Cloud 事件为反射可选联动，不引 spring-cloud-context 依赖）。
- 安全影响：配置可能含敏感信息，默认脱敏；端点受既有 `archimedes.api.enabled` 总开关与宿主安全框架（功能 6 已接入）保护，另有 `archimedes.config.enabled` 独立开关。
