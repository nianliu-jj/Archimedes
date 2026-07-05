package io.github.nianliu.archimedes.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.Map;

/**
 * 挂在 root logger 上的结构化采集 Appender：直接消费 ILoggingEvent 与其 MDC，
 * 与用户日志输出格式完全解耦；MDC 无 traceId 的事件不采集。
 */
public class ArchimedesLogAppender extends AppenderBase<ILoggingEvent> {

    public static final String APPENDER_NAME = "ARCHIMEDES_CAPTURE";

    private final LogStore store;
    private final String traceIdKey;
    private final String spanIdKey;

    public ArchimedesLogAppender(LogStore store, String traceIdKey, String spanIdKey) {
        this.store = store;
        this.traceIdKey = traceIdKey;
        this.spanIdKey = spanIdKey;
        setName(APPENDER_NAME);
    }

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc.get(traceIdKey);
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
                event.getFormattedMessage()));
    }
}
