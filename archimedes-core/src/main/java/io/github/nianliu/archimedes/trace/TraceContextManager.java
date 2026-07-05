package io.github.nianliu.archimedes.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * trace 上下文编排：按解析链确定 traceId（resolver → 请求头 → 宿主 MDC → 生成器），
 * 写入 MDC 并返回可精准回滚的 {@link TraceScope}。
 */
public class TraceContextManager {

    private final TraceProperties properties;
    private final TraceIdGenerator generator;
    private final TraceIdResolver resolver;

    public TraceContextManager(TraceProperties properties, TraceIdGenerator generator, TraceIdResolver resolver) {
        this.properties = properties;
        this.generator = generator;
        this.resolver = resolver;
    }

    public TraceScope begin(TraceRequest request) {
        String traceId = null;
        if (resolver != null) {
            traceId = normalize(resolver.resolve(request));
        }
        if (traceId == null) {
            traceId = normalize(request.getHeader(properties.getHeaderName()));
        }
        if (traceId == null && properties.isUseProjectTraceId()) {
            String hostOwned = normalize(MDC.get(properties.getMdcKey()));
            if (hostOwned != null) {
                // 宿主自有 traceId 体系已就位：不写不清，仅透出 traceId 供响应头回写
                return new TraceScope.Builder().build(hostOwned);
            }
        }
        if (traceId == null) {
            traceId = generator.generate();
        }

        TraceScope.Builder scope = new TraceScope.Builder();
        scope.put(properties.getMdcKey(), traceId);
        scope.put(properties.getSpanIdKey(), newSpanId());
        return scope.build(traceId);
    }

    private String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
