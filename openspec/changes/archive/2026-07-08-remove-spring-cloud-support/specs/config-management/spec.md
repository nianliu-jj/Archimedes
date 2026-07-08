## MODIFIED Requirements

### Requirement: 热更新联动刷新
配置更新成功后，系统 SHALL 对 prefix 命中变更 key 的 `@ConfigurationProperties` Bean 执行原地重绑定（构造器绑定的 Bean SHALL 防御式跳过并记录 WARN）；SHALL 发布 `ArchimedesConfigChangedEvent`（携带变更 key 集合）。系统 SHALL NOT 发布任何 Spring Cloud 事件（本依赖不对 Spring Cloud 提供支持；需要联动的宿主可自行监听 `ArchimedesConfigChangedEvent` 转发）。

#### Scenario: ConfigurationProperties Bean 重绑定
- **WHEN** 存在 prefix 为 `demo.conf` 的 `@ConfigurationProperties` JavaBean，更新 `demo.conf.title`
- **THEN** 该 Bean 实例的 `getTitle()` 立即返回新值，且响应的 `refreshedBeans` 包含该 Bean 名称

#### Scenario: 发布变更事件
- **WHEN** 任一配置更新成功
- **THEN** 容器内监听器可收到 `ArchimedesConfigChangedEvent` 且事件的 keys 包含被更新的 key，且不发布其它框架的环境变更事件
