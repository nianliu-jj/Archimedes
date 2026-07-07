package io.github.nianliu.archimedes.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;

/**
 * ResultSet 代理处理器：统计 {@code next()} 返回 true 的次数即实际取回行数，
 * 在 {@code close()} 时回填到所属 {@link SqlExecutionRecord}（查询执行时行数未知，记 -1 起始）。
 * 未关闭的结果集行数保持 -1（前端语义为"未知"）。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
class MonitoringResultSetHandler implements InvocationHandler {

    private final ResultSet target;
    private final SqlExecutionRecord record;
    /** 已取回行数（单结果集仅归属单线程遍历，无需原子类型）。 */
    private long fetchedRows;
    /** 防止重复 close 重复回填。 */
    private boolean closed;

    MonitoringResultSetHandler(ResultSet target, SqlExecutionRecord record) {
        this.target = target;
        this.record = record;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            Object result = method.invoke(target, args);
            if ("next".equals(name) && Boolean.TRUE.equals(result)) {
                fetchedRows++;
            } else if ("close".equals(name) && !closed) {
                closed = true;
                // 只在行数仍未知时回填，避免覆盖上游语义
                if (record.getRows() < 0) {
                    record.setRows(fetchedRows);
                }
            }
            return result;
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
