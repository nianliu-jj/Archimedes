package io.github.nianliu.archimedes.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * trace 上下文编排：按解析链确定 traceId（resolver → 请求头 → 宿主 MDC → 生成器），
 * 写入 MDC 并返回可精准回滚的 {@link TraceScope}。
 *
 * <p>设计要点：解析链按优先级从高到低串联，任一环节命中即短路；写入 MDC 前会记录旧值，
 * 保证请求结束时能精准回滚，避免污染宿主自有的 MDC 上下文。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class TraceContextManager {

    private final TraceProperties properties;
    private final TraceIdGenerator generator;
    private final TraceIdResolver resolver;

    /**
     * @param properties trace 配置项
     * @param generator  traceId 兜底生成器
     * @param resolver   用户自定义解析器，可为 null
     */
    public TraceContextManager(TraceProperties properties, TraceIdGenerator generator, TraceIdResolver resolver) {
        this.properties = properties;
        this.generator = generator;
        this.resolver = resolver;
    }

    /**
     * 开启一次请求的 trace 上下文：按解析链确定 traceId，写入 MDC，并返回可回滚的 scope。
     *
     * @param request 请求抽象
     * @return trace 上下文范围，需在请求结束时 close() 以回滚 MDC
     */
    public TraceScope begin(TraceRequest request) {
        String traceId = null;
        // 第一优先级：用户自定义解析器（若已注册）
        if (resolver != null) {
            traceId = normalize(resolver.resolve(request));
        }
        // 第二优先级：从约定的请求头透传（上游服务传入）
        if (traceId == null) {
            traceId = normalize(request.getHeader(properties.getHeaderName()));
        }
        // 第三优先级：信任宿主自有 traceId 体系（仅当开关开启且 MDC 已有值）
        if (traceId == null && properties.isUseProjectTraceId()) {
            String hostOwned = normalize(MDC.get(properties.getMdcKey()));
            if (hostOwned != null) {
                // 宿主自有 traceId 体系已就位：不写不清，仅透出 traceId 供响应头回写
                return new TraceScope.Builder().build(hostOwned);
            }
        }
        // 兜底：以上均未命中，生成全新 traceId
        if (traceId == null) {
            traceId = generator.generate();
        }

        TraceScope.Builder scope = new TraceScope.Builder();
        scope.put(properties.getMdcKey(), traceId);
        // 每次 begin 都生成新的 spanId，用于标识本次调用节点
        scope.put(properties.getSpanIdKey(), newSpanId());
        return scope.build(traceId);
    }

    /**
     * 生成 spanId：取 UUID 前 16 位十六进制，比 traceId 更短，标识单个调用节点。
     */
    private String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 归一化字符串：去除首尾空白，空串统一转为 null，便于解析链以 null 判断是否命中。
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
