package io.github.nianliu.archimedes.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.Map;

/**
 * 挂在 root logger 上的结构化采集 Appender：直接消费 ILoggingEvent 与其 MDC，
 * 与用户日志输出格式完全解耦；MDC 无 traceId 的事件不采集。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class ArchimedesLogAppender extends AppenderBase<ILoggingEvent> {

    /** Appender 名称（用于在 logback 上下文中标识本采集器）。 */
    public static final String APPENDER_NAME = "ARCHIMEDES_CAPTURE";

    /** 结构化日志的落地存储。 */
    private final LogStore store;
    /** MDC 中 traceId 的键名（由 trace 配置注入，未含此键的事件被忽略）。 */
    private final String traceIdKey;
    /** MDC 中 spanId 的键名。 */
    private final String spanIdKey;

    public ArchimedesLogAppender(LogStore store, String traceIdKey, String spanIdKey) {
        this.store = store;
        this.traceIdKey = traceIdKey;
        this.spanIdKey = spanIdKey;
        setName(APPENDER_NAME);
    }

    /**
     * 每条日志事件的采集回调：从 MDC 取 traceId，无则直接跳过（非链路上下文日志不入库），
     * 否则拆解为结构化 LogEntry 落库。
     */
    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc.get(traceIdKey);
        // 无 traceId 无法归档聚合，直接丢弃，避免污染按链路查询的存储
        if (traceId == null || traceId.isEmpty()) {
            return;
        }
        store.append(new LogEntry(
                event.getTimeStamp(),
                traceId,
                mdc.get(spanIdKey),
                String.valueOf(event.getLevel()),
                event.getThreadName(),
                event.getLoggerName(),
                event.getFormattedMessage())); // 取已渲染消息，与占位符实参无关
    }
}
