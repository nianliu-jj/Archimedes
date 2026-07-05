package io.github.nianliu.archimedes.trace;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次请求的 trace 上下文范围。记录本请求写入 MDC 前各 key 的旧值，
 * close() 时恢复旧值或移除——绝不使用 MDC.clear()，不破坏宿主自有 MDC 上下文。
 */
public class TraceScope implements AutoCloseable {

    private final String traceId;

    /** key → 写入前的旧值（null 表示写入前不存在）。 */
    private final Map<String, String> previousValues;

    TraceScope(String traceId, Map<String, String> previousValues) {
        this.traceId = traceId;
        this.previousValues = previousValues;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : previousValues.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }

    static class Builder {
        private final Map<String, String> previous = new LinkedHashMap<>();

        void put(String key, String value) {
            if (!previous.containsKey(key)) {
                previous.put(key, MDC.get(key));
            }
            MDC.put(key, value);
        }

        TraceScope build(String traceId) {
            return new TraceScope(traceId, previous);
        }
    }
}
