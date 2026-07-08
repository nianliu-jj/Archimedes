## 1. core：移除反射发布与测试桩

- [x] 1.1 `DynamicConfigManager`：删除 `CLOUD_EVENT_CLASS` 常量、反射发布块与相关 import/javadoc，`publishEvents` 收窄为仅发布 `ArchimedesConfigChangedEvent`
- [x] 1.2 `ArchimedesConfigChangedEvent` javadoc 移除 Spring Cloud 联动描述
- [x] 1.3 删除测试桩 `org/springframework/cloud/context/environment/EnvironmentChangeEvent.java`；`DynamicConfigManagerTest` 断言收窄（仅自有事件）

## 2. 文档与验证

- [x] 2.1 README 事件联动说明收窄；docs/功能清单与任务列表.md（事件条目/16.4 措辞、移除"Spring Cloud 兼容性验证"待办与 Slice 14.3、锁定决策表补记不支持 Spring Cloud）
- [x] 2.2 全模块 `mvn test` 全绿
