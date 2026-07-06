# Tasks: trace-core

## 1. core trace 包

- [x] 1.1 `trace/TraceProperties`（archimedes.trace.*，六项配置）
- [x] 1.2 `trace/TraceIdGenerator` + `UuidTraceIdGenerator`；`trace/TraceIdResolver`；`trace/TraceRequest`（单方法抽象）
- [x] 1.3 `trace/TraceContextManager` + `TraceScope`：解析链（resolver → header → 宿主 MDC[useProjectTraceId] → generator）、MDC 写入、按 key 精准清理
- [x] 1.4 core 单测：解析链四级矩阵、精准清理（宿主键不受影响）、spanId 写入、useProjectTraceId 语义

## 2. starter 装配

- [x] 2.1 sb3-starter：jakarta `TraceIdFilter` 薄壳 + `ArchimedesTraceAutoConfiguration`（FilterRegistrationBean，HIGHEST_PRECEDENCE，仅 REQUEST dispatch）+ imports 注册行
- [x] 2.2 sb2-starter：javax 镜像 + spring.factories 注册行
- [x] 2.3 自动装配测试：enabled=false 零 Bean；默认装配存在 Filter 与 manager

## 3. 集成测试与样例

- [x] 3.1 sb3 集成测试：自动生成+响应头一致、请求头透传、自定义 TraceIdGenerator Bean 生效、自定义 header-name、请求后 MDC 清理
- [x] 3.2 sb2 镜像集成测试（Java 8 语法）
- [x] 3.3 example×2：`/api/trace/current` 演示端点（返回 MDC 中的 traceId）
- [x] 3.4 全量 `mvn clean install` 全绿 + example 真机 curl 验证响应头与 body 一致

## 4. 收尾

- [x] 4.1 README trace 章节（配置表 + SPI 说明）
- [x] 4.2 功能清单勾选 Slice 4 对应项
