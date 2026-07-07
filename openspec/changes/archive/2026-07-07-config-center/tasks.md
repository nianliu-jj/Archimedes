## 1. core：env 包核心组件（TDD：先测后实现）

- [x] 1.1 `ConfigManagementProperties`（archimedes.config.*：enabled / hot-refresh-enabled / sensitive-keys）+ 绑定单测
- [x] 1.2 `EnvironmentConfigService`：EnumerablePropertySource 枚举 + 来源标注 + contains 脱敏 + 防御式取值；单测覆盖枚举/排序/脱敏/自定义关键字/异常项跳过
- [x] 1.3 `DynamicConfigManager`：动态 MapPropertySource（addFirst、get-or-create）、update/remove、变更明细返回；单测覆盖新增/覆盖/删除恢复
- [x] 1.4 `ConfigurationPropertiesRebinder`：prefix 命中收集 + Binder 原地重绑定 + 构造器绑定跳过 WARN；单测用真实 @ConfigurationProperties Bean 验证
- [x] 1.5 `ArchimedesConfigChangedEvent` + Spring Cloud `EnvironmentChangeEvent` 反射发布（同 FQCN 测试桩驱动反射路径）；事件发布单测

## 2. core：端点与 UI

- [x] 2.1 `ArchimedesConfigController`：GET /config 与 POST /config/update（400/403 语义、脱敏一致性），MockMvc 单测
- [x] 2.2 `index.html` 新增「配置中心」Tab：分组表格 + 搜索 + 行内编辑应用 + 动态覆盖高亮/移除 + 热更新关闭禁用编辑

## 3. starter：双端装配与 e2e

- [x] 3.1 SB3 `ArchimedesConfigAutoConfiguration`（@ConditionalOnWebApplication 不限 type + 双开关）+ AutoConfiguration.imports 注册
- [x] 3.2 SB2 同名装配 + spring.factories 注册
- [x] 3.3 SB3 e2e：真 HTTP 查询/脱敏/热更新/重绑定/开关关闭 403；REACTIVE 分支 e2e 验证同等可用
- [x] 3.4 SB2 e2e：镜像 SB3 servlet 用例（javax 栈）

## 4. 验证与收尾

- [x] 4.1 全模块 `mvn test` 全绿；example-all 补配置中心演示配置项
- [x] 4.2 更新 README（配置中心章节 + archimedes.config.* 配置表 + 非持久化边界声明）与 docs/功能清单与任务列表.md
