package io.github.nianliu.archimedes.trace;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次请求的 trace 上下文范围。记录本请求写入 MDC 前各 key 的旧值，
 * close() 时恢复旧值或移除——绝不使用 MDC.clear()，不破坏宿主自有 MDC 上下文。
 *
 * <p>设计要点：实现 {@link AutoCloseable}，配合 try-with-resources 使用，
 * 保证无论请求处理是否抛异常，MDC 都能被精准回滚到 begin 之前的状态。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class TraceScope implements AutoCloseable {

    private final String traceId;

    /** key → 写入前的旧值（null 表示写入前不存在）。 */
    private final Map<String, String> previousValues;

    /**
     * @param traceId        本次请求确定的 traceId
     * @param previousValues 各 MDC key 写入前的旧值快照，供 close() 回滚
     */
    TraceScope(String traceId, Map<String, String> previousValues) {
        this.traceId = traceId;
        this.previousValues = previousValues;
    }

    /**
     * 获取本次请求的 traceId，供响应头回写等场景使用。
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 结束 trace 上下文：逐 key 精准回滚 MDC。
     */
    @Override
    public void close() {
        for (Map.Entry<String, String> entry : previousValues.entrySet()) {
            // 旧值为 null 说明写入前该 key 不存在，回滚时应移除而非置空，避免残留脏 key
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                // 写入前有值：恢复原值，保护宿主原有的 MDC 上下文
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * TraceScope 构造器：负责在写入 MDC 的同时记录旧值快照。
     */
    static class Builder {
        private final Map<String, String> previous = new LinkedHashMap<>();

        /**
         * 写入一个 MDC key，并在首次写入前记录其旧值。
         *
         * @param key   MDC key
         * @param value 要写入的新值
         */
        void put(String key, String value) {
            // 仅在首次写入该 key 时记录旧值，避免同一 key 多次写入后旧值被新值覆盖导致回滚错误
            if (!previous.containsKey(key)) {
                previous.put(key, MDC.get(key));
            }
            MDC.put(key, value);
        }

        /**
         * 构建 TraceScope，交出旧值快照的所有权。
         *
         * @param traceId 本次请求确定的 traceId
         */
        TraceScope build(String traceId) {
            return new TraceScope(traceId, previous);
        }
    }
}
