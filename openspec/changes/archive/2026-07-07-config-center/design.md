## Context

Archimedes 1.0 已完成契约扫描、链路日志、日志采集/查询、内置控制台。`docs/需求.md` §3 要求的配置中心（展示 + 热更新）为 1.1 版本缺口。约束：

- `archimedes-core` 以 SB 2.7.18 编译面、`--release 8`、零 servlet import，单 jar 同时服务 SB2/SB3 双 starter。
- 控制台端点均为纯注解式 `@RestController`（SERVLET 与 REACTIVE 分支复用同一控制器类）。
- 不新增强依赖；可选生态一律 `@ConditionalOnClass` / 反射防御式接入（先例：SOFA/tRPC 扫描器、Swagger 注解读取）。
- UI 为单文件 ES5 `index.html`，`__ARCHIMEDES_API_URL__` 占位符注入，Tab 结构（nav#tabs + section.panel + TABS 数组）。

## Goals / Non-Goals

**Goals:**

- 全量配置可视化：枚举 `ConfigurableEnvironment` 中全部 `EnumerablePropertySource`，按优先级顺序输出 key/value/来源；敏感值脱敏。
- 运行时热更新：写入最高优先级动态属性源，立即影响 `environment.getProperty()`；命中前缀的 `@ConfigurationProperties` Bean 原地重绑定；发布事件（自有事件必发，Spring Cloud `EnvironmentChangeEvent` 存在则反射同步发布）。
- 可回退：动态覆盖可单键删除，恢复底层配置源原值。
- UI 配置中心 Tab：搜索、按来源分组展示、行内编辑应用、动态覆盖高亮。

**Non-Goals:**

- 不持久化动态覆盖（重启即失效，README 声明）；不写回 application.properties 文件。
- 无 spring-cloud 时不做 `@RefreshScope` Bean 重建（仅重绑定既有实例；构造器绑定 Bean 跳过并 WARN）。
- 不展示非枚举型属性源（与 actuator env 端点同等限制）。

## Decisions

1. **包与组件划分**（core 新增 `env` 包，命名对齐既有风格）
   - `ConfigManagementProperties`（`archimedes.config.*`）：`enabled`（默认 true）、`hot-refresh-enabled`（默认 true）、`sensitive-keys`（默认 password/secret/token/credential/key，用户可整体替换）。
   - `EnvironmentConfigService`：只读枚举 + 脱敏。脱敏规则 = key 小写后 **contains** 任一关键字（对齐 Spring Boot Sanitizer 的宽松取向，宁可误脱敏不可漏）。
   - `DynamicConfigManager`：get-or-create 名为 `archimedesDynamicConfig` 的 `MapPropertySource` 并 `addFirst`；`update(key, value)` / `remove(key)`；变更后依次触发重绑定与事件发布；返回变更明细（旧生效值、新值、重绑定的 Bean 名列表）。
   - `ConfigurationPropertiesRebinder`（轻量版）：`getBeansWithAnnotation(ConfigurationProperties.class)` 收集 prefix → 变更 key 命中 prefix 时 `Binder.get(env).bind(prefix, Bindable.ofInstance(bean))` 原地重绑定。选择原地绑定而非 spring-cloud 的 destroy/reinit：零依赖、不破坏既有引用；代价是构造器绑定 Bean 无法刷新（防御式跳过）。
   - 事件：自定义 `ArchimedesConfigChangedEvent(Set<String> keys)` 必发；`Class.forName("org.springframework.cloud.context.environment.EnvironmentChangeEvent")` 命中时反射 `new (Set)` 并发布——与 TR 扫描器同款"零编译依赖 + 同 FQCN 测试桩"策略验证。
2. **端点**（core `web` 包 `ArchimedesConfigController`，纯注解零 servlet 依赖）
   - `GET {base}/config`：`{hotRefreshEnabled, dynamicKeys, propertySources:[{name, entries:[{key, value, sensitive}]}]}`，属性源按 Environment 优先级顺序输出。
   - `POST {base}/config/update`：body `{key, value}`；`value=null/缺省` 表示删除动态覆盖。`hot-refresh-enabled=false` 时返回 403；key 空返回 400。响应含 `{key, oldValue, newValue, refreshedBeans}`（敏感 key 的新旧值同样脱敏）。
   - 路径沿用 `${archimedes.api.base-path:...}` 占位符模式，天然被契约扫描的 base-path 排除规则覆盖。
3. **装配**：新增 `ArchimedesConfigAutoConfiguration` ×2 starter，`@ConditionalOnWebApplication`（不限 type，SERVLET/REACTIVE 通吃）+ `archimedes.api.enabled` + `archimedes.config.enabled` 双开关；SB3 走 AutoConfiguration.imports，SB2 走 spring.factories。
4. **值序列化**：仅迭代 `EnumerablePropertySource`；`String.valueOf(getProperty(key))` 防御式转换（OriginTrackedValue toString 即原值），单 key 异常不中断整表。
5. **UI**：TABS 增加 `config`；表格按属性源分组 + 全局搜索框；动态源置顶高亮；行内"编辑→应用"调用 update 端点后局部刷新。

## Risks / Trade-offs

- [重绑定原地修改单例 Bean，并发读线程可能看到中间状态] → JavaBean setter 原子性足够（与 spring-cloud rebinder 同级风险）；文档提示热更新适用于开发/联调场景。
- [contains 脱敏误伤（如 `mdc-key` 含 key）] → 宁可多脱敏；`sensitive-keys` 可由用户整体替换收窄。
- [POST 明文提交敏感值] → 端点受宿主安全框架（功能 6）与 `archimedes.config.*` 开关保护；README 提示生产环境收敛。
- [systemEnvironment 等大属性源全量返回] → 单次快照、无缓存；UI 端搜索过滤，暂不做服务端分页（数据量与 actuator env 同级）。

## Migration Plan

纯新增能力，默认开启但不改变任何既有行为；`archimedes.config.enabled=false` 可整体关闭回退。
