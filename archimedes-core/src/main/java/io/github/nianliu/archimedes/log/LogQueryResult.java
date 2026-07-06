package io.github.nianliu.archimedes.log;

import java.util.List;

/**
 * 按 traceId 查询的分页结果。
 *
 * <p>不可变数据载体：封装一次查询的回显参数（traceId/page/size）、命中总数（total）
 * 与当前页的日志列表（logs），便于前端渲染分页控件。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class LogQueryResult {

    /** 本次查询的目标链路 ID（回显）。 */
    private final String traceId;
    /** 该 traceId 下命中的日志总条数（用于计算总页数）。 */
    private final int total;
    /** 当前页码（1 起始，回显）。 */
    private final int page;
    /** 每页大小（回显）。 */
    private final int size;
    /** 当前页的日志明细。 */
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
