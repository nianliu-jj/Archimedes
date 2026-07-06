# Design: log-config-fallback

## Context

Spring Boot 的日志初始化在 `ApplicationEnvironmentPreparedEvent` 阶段由 `LoggingApplicationListener` 完成：若有 `logging.config` 属性用之，否则按约定名（logback-spring.xml 等）在 classpath 发现，都没有则用 Boot 内置默认。需求文档给的方案（`System.setProperty("logback.configurationFile")`）绕过了 Boot 的日志体系且时机不可靠，explore 阶段已否决，改用 **EnvironmentPostProcessor 注入 `logging.config`**。

## Goals / Non-Goals

**Goals:**
- 用户零配置时：pattern 含 traceId/spanId + 滚动文件输出，`archimedes.log.*` 四项可调
- 用户有任何自有日志配置时：完全不介入（优先级最高的护栏）
- SB2/SB3 单份实现（core 承载）

**Non-Goals:**
- log4j2/JUL 的兜底（logback-only）
- 运行时动态刷新 pattern（需求文档提及"动态刷新"，属后续增强，本 slice 不做——改配置重启生效）

## Decisions

### D1：资源名 `archimedes-logback.xml`，不叫 logback-spring.xml

jar 里若放 `logback-spring.xml`，Boot 的约定发现会把它当宿主配置拾取，**强行覆盖所有宿主**（包括不想要我们配置的）。改名后只有 EPP 显式注入 `logging.config` 时才生效，介入与否完全由检测逻辑控制。

### D2：检测与让位规则（任一命中即不介入）

1. `logging.config` 属性已存在（用户显式指定，含命令行/环境变量）
2. classpath 存在 `logback-spring.xml` / `logback.xml` / `logback-spring.groovy` / `logback.groovy` / `logback-test.xml`
3. `ch.qos.logback.classic.LoggerContext` 不在 classpath（非 logback 实现）
4. `archimedes.log.fallback-enabled=false`（逃生开关，默认 true）

注入方式：`MapPropertySource("archimedesLogging")` **addLast**——保证用户任何显式配置（哪怕检测竞态）优先级都更高。

### D3：`<springProperty>` 读取 `archimedes.log.*`

Boot 经 `logging.config` 初始化 logback 时使用 `SpringBootJoranConfigurator`，`<springProperty>` 可用（与文件名无关）。四项：

| source | 默认值 |
|---|---|
| `archimedes.log.pattern` | `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] [%X{spanId:-}] %-5level %logger{36} - %msg%n` |
| `archimedes.log.path` | `./logs` |
| `archimedes.log.max-history` | `30` |
| `archimedes.log.max-file-size` | `100MB` |

FILE appender 用 `SizeAndTimeBasedRollingPolicy`（logback 1.2/1.5 均可用），`application.%d{yyyy-MM-dd}.%i.log`。

### D4：EPP 注册走 core 的 spring.factories

`org.springframework.boot.env.EnvironmentPostProcessor` key 的 spring.factories 机制在 SB2 与 SB3 都有效（SB3 移除的只是 auto-configuration 的 spring.factories key）。放 core 一份，双 starter 自动继承，无重复。EPP 实现 `Ordered`，order 取 `HIGHEST_PRECEDENCE + 10` 内的常规值即可（同类 EPP 无顺序敏感）。

### D5：测试的模块分工

test resources 是模块全局的，无法按测试类增删 classpath 文件，因此：sb3 模块不放用户配置 → 断言兜底注入生效（`logging.config` 属性 + root logger 挂上 ARCHIMEDES_CONSOLE/ARCHIMEDES_FILE appender + pattern 含 traceId）；sb2 模块 test resources 放一份最小 `logback-spring.xml` → 断言我们未注入 `logging.config` 且用户配置在用。两分支各有真实容器验证。starter 测试模块加 `application.properties` 设 `archimedes.log.path=target/test-logs`，避免测试在模块根目录写 `logs/`。

## Risks / Trade-offs

- [无自有配置的宿主新增了文件输出（行为变化）] → 需求明确要求；README 说明 + `archimedes.log.fallback-enabled=false` 逃生
- [宿主用 spring.profiles 分环境放不同 logback 文件] → 只要任一约定名存在即让位，规则保守
- [`logback-test.xml` 在宿主测试期存在] → 让位（正确：测试期宿主自有配置优先）
- [EPP 在 bootstrap 阶段多次调用（Spring Cloud context）] → 注入幂等（属性源按名存在即跳过）

## Migration Plan

1. core：xml 资源 + EPP + spring.factories + 单测
2. sb2 test resources 放用户配置样张；双端集成断言两分支；`.gitignore` + 测试日志路径
3. 全量构建 + example 真机验证（控制台 pattern 含 traceId、logs 文件生成、archimedes.log.pattern 自定义生效）

## Open Questions

（无）
