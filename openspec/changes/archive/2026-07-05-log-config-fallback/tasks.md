# Tasks: log-config-fallback

## 1. core 实现

- [x] 1.1 `resources/archimedes-logback.xml`（CONSOLE + 滚动 FILE，springProperty 四项，pattern 含 traceId/spanId）
- [x] 1.2 `log/ArchimedesLoggingEnvironmentPostProcessor`（四条让位规则 + addLast 注入 + 幂等）
- [x] 1.3 core `META-INF/spring.factories` 注册 EPP（SB2/SB3 通用）
- [x] 1.4 core 单测：logging.config 已设让位、fallback-enabled=false 让位、注入分支与幂等（classpath 文件检测经真实资源验证于 starter 层）

## 2. 双端验证

- [x] 2.1 sb3 集成测试（模块无用户配置）：logging.config 被注入、root logger 含 ARCHIMEDES 双 appender、自定义 archimedes.log.pattern 生效
- [x] 2.2 sb2 test resources 放最小 logback-spring.xml，集成测试断言未注入 logging.config、宿主配置在用
- [x] 2.3 starter 测试模块 application.properties 导流日志到 target/test-logs；`.gitignore` 增加 `logs/`
- [x] 2.4 全量构建全绿 + example 真机验证（控制台 pattern 含 traceId、logs 文件生成）

## 3. 收尾

- [x] 3.1 README 日志配置章节（兜底规则、archimedes.log.* 表、逃生开关）
- [x] 3.2 功能清单勾选 Slice 7 + spec 同步 + 归档 + 提交
