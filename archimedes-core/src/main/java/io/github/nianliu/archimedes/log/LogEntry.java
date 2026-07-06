package io.github.nianliu.archimedes.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 单条被采集的日志（结构化字段，与输出格式解耦）。
 *
 * <p>设计要点：所有字段均为 final 不可变，构造后即为快照，天然线程安全，
 * 可安全地在采集线程与查询线程之间共享。时间戳以毫秒数（timestampMillis）存储，
 * 展示用的字符串（getTimestamp）在读取时才按系统时区格式化，避免存储阶段绑定格式。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class LogEntry {

    /** 时间戳展示格式：毫秒精度 + 系统默认时区（仅用于 getTimestamp 的懒格式化）。 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** 日志产生时刻（epoch 毫秒），作为排序与格式化的原始依据。 */
    private final long timestampMillis;
    /** 关联的链路追踪 ID，用于按 traceId 聚合查询。 */
    private final String traceId;
    /** 关联的 span ID，标识链路中的具体节点。 */
    private final String spanId;
    /** 日志级别（如 INFO/WARN/ERROR）。 */
    private final String level;
    /** 产生日志的线程名。 */
    private final String thread;
    /** logger 名称（通常为类的全限定名）。 */
    private final String logger;
    /** 已渲染的日志正文。 */
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

    /** 将毫秒时间戳按系统时区懒格式化为可读字符串（供 UI 展示）。 */
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
