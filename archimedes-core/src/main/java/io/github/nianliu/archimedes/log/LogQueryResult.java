package io.github.nianliu.archimedes.log;

import java.util.List;

/** 按 traceId 查询的分页结果。 */
public class LogQueryResult {

    private final String traceId;
    private final int total;
    private final int page;
    private final int size;
    private final List<LogEntry> logs;

    public LogQueryResult(String traceId, int total, int page, int size, List<LogEntry> logs) {
        this.traceId = traceId;
        this.total = total;
        this.page = page;
        this.size = size;
        this.logs = logs;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public List<LogEntry> getLogs() {
        return logs;
    }
}
