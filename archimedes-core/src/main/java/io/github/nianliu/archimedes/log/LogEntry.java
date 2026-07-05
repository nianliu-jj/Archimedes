package io.github.nianliu.archimedes.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 单条被采集的日志（结构化字段，与输出格式解耦）。 */
public class LogEntry {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final long timestampMillis;
    private final String traceId;
    private final String spanId;
    private final String level;
    private final String thread;
    private final String logger;
    private final String message;

    public LogEntry(long timestampMillis, String traceId, String spanId, String level,
                    String thread, String logger, String message) {
        this.timestampMillis = timestampMillis;
        this.traceId = traceId;
        this.spanId = spanId;
        this.level = level;
        this.thread = thread;
        this.logger = logger;
        this.message = message;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public String getTimestamp() {
        return FORMATTER.format(Instant.ofEpochMilli(timestampMillis));
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getLevel() {
        return level;
    }

    public String getThread() {
        return thread;
    }

    public String getLogger() {
        return logger;
    }

    public String getMessage() {
        return message;
    }
}
