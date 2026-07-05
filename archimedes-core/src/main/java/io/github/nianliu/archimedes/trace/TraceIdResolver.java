package io.github.nianliu.archimedes.trace;

/**
 * 用户自定义 traceId 解析 SPI：从请求中按项目自有规则解析 traceId（优先级最高）。
 * 返回 null/空串表示放弃，落入后续解析链（请求头 → 宿主 MDC → 生成器）。
 */
public interface TraceIdResolver {

    String resolve(TraceRequest request);
}
