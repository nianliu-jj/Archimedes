package io.github.nianliu.archimedes.jdbc;

import java.util.List;

/**
 * 单次 SQL 执行明细：语句、参数、耗时、类型、行数、成败与 traceId 关联。
 * <p>{@code rows} 语义：UPDATE/BATCH 为影响行数；QUERY 初始为 -1（未知），
 * ResultSet 关闭时由代理回填为实际取回行数（volatile 保证注册表读线程可见）。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class SqlExecutionRecord {

    /** 执行类型：查询。 */
    public static final String TYPE_QUERY = "QUERY";
    /** 执行类型：更新（含 DDL/DML 的 executeUpdate 族）。 */
    public static final String TYPE_UPDATE = "UPDATE";
    /** 执行类型：批处理。 */
    public static final String TYPE_BATCH = "BATCH";
    /** 执行类型：通用 execute（返回布尔，可能是查询也可能是更新）。 */
    public static final String TYPE_EXECUTE = "EXECUTE";

    /** 所属数据源 Bean 名。 */
    private final String dataSource;
    /** 归一化后的 SQL（空白折叠）。 */
    private final String sql;
    /** 绑定参数渲染值（capture-parameters=false 或无参数时为 null）。 */
    private final List<String> params;
    /** 执行开始时间（epoch 毫秒）。 */
    private final long startTime;
    /** 执行耗时（毫秒）。 */
    private final long durationMillis;
    /** 执行类型（QUERY/UPDATE/BATCH/EXECUTE）。 */
    private final String type;
    /** 是否执行成功。 */
    private final boolean success;
    /** 失败时的异常消息（成功为 null）。 */
    private final String errorMessage;
    /** 执行线程 MDC 中的 traceId（无则为 null，与链路日志互相印证）。 */
    private final String traceId;
    /** 是否慢 SQL（耗时 >= slow-sql-millis）。 */
    private final boolean slow;
    /** 行数：更新=影响行数；查询=-1 起始、ResultSet close 时回填取回行数。 */
    private volatile long rows;

    public SqlExecutionRecord(String dataSource, String sql, List<String> params,
                              long startTime, long durationMillis, String type,
                              long rows, boolean success, String errorMessage,
                              String traceId, boolean slow) {
        this.dataSource = dataSource;
        this.sql = sql;
        this.params = params;
        this.startTime = startTime;
        this.durationMillis = durationMillis;
        this.type = type;
        this.rows = rows;
        this.success = success;
        this.errorMessage = errorMessage;
        this.traceId = traceId;
        this.slow = slow;
    }

    public String getDataSource() {
        return dataSource;
    }

    public String getSql() {
        return sql;
    }

    public List<String> getParams() {
        return params;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getType() {
        return type;
    }

    public long getRows() {
        return rows;
    }

    /** 查询行数回填入口（ResultSet 代理在 close 时调用）。 */
    public void setRows(long rows) {
        this.rows = rows;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTraceId() {
        return traceId;
    }

    public boolean isSlow() {
        return slow;
    }
}
