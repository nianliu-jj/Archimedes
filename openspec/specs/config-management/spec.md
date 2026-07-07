# config-management Specification

## Purpose
TBD - created by archiving change config-center. Update Purpose after archive.
## Requirements
### Requirement: 配置全量展示端点
系统 SHALL 提供 `GET {base-path}/config` 端点，按 Environment 优先级顺序枚举全部 `EnumerablePropertySource`，返回每个属性源的名称与其全部配置项（key / value / 是否敏感），并返回热更新开关状态与当前动态覆盖键列表。非枚举型属性源 SHALL 被跳过且不影响其余输出；单个配置项取值异常 SHALL 不中断整体响应。

#### Scenario: 查询全部配置
- **WHEN** 客户端请求 `GET {base-path}/config`
- **THEN** 响应 JSON 包含 `propertySources` 数组（按优先级排序，每项含 `name` 与 `entries`），application.properties 中声明的配置项出现在对应属性源下，且 `hotRefreshEnabled` 与 `dynamicKeys` 字段存在

#### Scenario: 配置总开关关闭
- **WHEN** 宿主配置 `archimedes.config.enabled=false`
- **THEN** 配置端点与相关 Bean 均不装配，`GET {base-path}/config` 返回 404

### Requirement: 敏感配置脱敏
系统 SHALL 对 key（小写化后）包含敏感关键字的配置项将 value 替换为 `******` 后输出，并标记 `sensitive=true`。默认关键字集合 SHALL 为 password、secret、token、credential、key；用户 SHALL 可通过 `archimedes.config.sensitive-keys` 整体替换该集合。热更新响应中的新旧值 SHALL 遵循同一脱敏规则。

#### Scenario: 默认关键字脱敏
- **WHEN** 配置中存在 `spring.datasource.password=123456`
- **THEN** `GET {base-path}/config` 返回该项 value 为 `******` 且 `sensitive=true`

#### Scenario: 自定义关键字
- **WHEN** 宿主配置 `archimedes.config.sensitive-keys=internal`，且存在配置 `app.internal.endpoint=x`
- **THEN** 该项被脱敏，而默认关键字（如 password）不再触发脱敏

### Requirement: 配置热更新
系统 SHALL 提供 `POST {base-path}/config/update` 端点接收 `{key, value}`：将键值写入名为 `archimedesDynamicConfig` 的动态 `MapPropertySource`（位于 Environment 最高优先级），使 `environment.getProperty(key)` 立即返回新值；`value` 为 null 或缺省 SHALL 删除该键的动态覆盖以恢复底层原值。key 为空 SHALL 返回 400；`archimedes.config.hot-refresh-enabled=false` 时 SHALL 返回 403 且不产生任何变更。响应 SHALL 包含变更明细（key、旧生效值、新值、重绑定的 Bean 名列表）。

#### Scenario: 更新配置立即生效
- **WHEN** 客户端 `POST {base-path}/config/update` 提交 `{"key":"app.greeting","value":"hi"}`
- **THEN** 响应 200，`environment.getProperty("app.greeting")` 返回 `hi`，且 `GET {base-path}/config` 的 `dynamicKeys` 包含 `app.greeting`

#### Scenario: 删除动态覆盖恢复原值
- **WHEN** 某 key 已被动态覆盖，客户端提交 `{"key":"<该key>","value":null}`
- **THEN** 动态覆盖被移除，`environment.getProperty` 恢复返回底层配置源的原值

#### Scenario: 热更新开关关闭
- **WHEN** 宿主配置 `archimedes.config.hot-refresh-enabled=false`，客户端调用 update 端点
- **THEN** 响应 403，Environment 与动态属性源无任何变化（GET 查询仍可用）

### Requirement: 热更新联动刷新
配置更新成功后，系统 SHALL 对 prefix 命中变更 key 的 `@ConfigurationProperties` Bean 执行原地重绑定（构造器绑定的 Bean SHALL 防御式跳过并记录 WARN）；SHALL 发布 `ArchimedesConfigChangedEvent`（携带变更 key 集合）；当 classpath 存在 Spring Cloud 的 `EnvironmentChangeEvent` 时 SHALL 反射构造并同步发布，classpath 不存在时 SHALL 静默跳过。

#### Scenario: ConfigurationProperties Bean 重绑定
- **WHEN** 存在 prefix 为 `demo.conf` 的 `@ConfigurationProperties` JavaBean，更新 `demo.conf.title`
- **THEN** 该 Bean 实例的 `getTitle()` 立即返回新值，且响应的 `refreshedBeans` 包含该 Bean 名称

#### Scenario: 发布变更事件
- **WHEN** 任一配置更新成功
- **THEN** 容器内监听器可收到 `ArchimedesConfigChangedEvent` 且事件的 keys 包含被更新的 key；classpath 存在 Spring Cloud `EnvironmentChangeEvent` 类时同名事件同步发布

### Requirement: 配置中心 UI Tab
内置控制台 SHALL 新增「配置中心」Tab：按属性源分组展示全部配置（动态覆盖源置顶并高亮标识）、提供全局文本搜索过滤、支持行内编辑值并点击应用调用热更新端点、支持对动态覆盖项一键移除；热更新开关关闭时编辑控件 SHALL 禁用。

#### Scenario: 查看与搜索
- **WHEN** 用户打开配置中心 Tab 并在搜索框输入关键字
- **THEN** 列表仅显示 key 或 value 命中关键字的配置项，且每项展示其来源属性源名称

#### Scenario: 行内编辑应用
- **WHEN** 用户修改某配置项的值并点击「应用」
- **THEN** 页面调用 update 端点，成功后刷新列表且该项出现动态覆盖标识

### Requirement: 双栈与双版本装配
配置中心 SHALL 在 SB2/SB3 双 starter 中以独立自动装配类注册，SERVLET 与 REACTIVE Web 应用均生效（控制器为纯注解式、零 servlet 依赖），并受 `archimedes.api.enabled` 总开关与 `archimedes.config.enabled` 独立开关控制。

#### Scenario: 响应式应用可用
- **WHEN** 宿主为 WebFlux 应用（REACTIVE 类型）且引入任一 starter
- **THEN** `GET {base-path}/config` 与 update 端点行为与 Servlet 栈一致

