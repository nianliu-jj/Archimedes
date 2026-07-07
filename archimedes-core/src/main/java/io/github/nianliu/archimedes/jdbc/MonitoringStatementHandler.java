package io.github.nianliu.archimedes.jdbc;

import org.slf4j.MDC;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Statement 族代理处理器：SQL 监控的核心拦截点。
 * <ul>
 *   <li>execute 族方法计时并产出 {@link SqlExecutionRecord}（成功/失败都记录，异常原样抛出）；</li>
 *   <li>{@code setXxx(index, value)} 捕获绑定参数（capture-parameters 可关）；</li>
 *   <li>{@code executeQuery} 的 ResultSet 再包代理，取回行数在 close 时回填；</li>
 *   <li>批处理：addBatch 计数/收集语句，executeBatch 记为一次 BATCH 执行（行数为各批影响行数之和）。</li>
 * </ul>
 * 边界：{@code execute()} 返回 true（结果集形态）时行数记 -1（其 getResultSet() 不代理）；
 * CallableStatement 的命名参数（String 下标）不采集。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
class MonitoringStatementHandler implements InvocationHandler {

    /** 参数渲染值的最大长度，超长截断（防大字段撑爆内存）。 */
    private static final int MAX_PARAM_LENGTH = 200;

    private final Statement target;
    /** prepared/prepareCall 的预置 SQL（已归一化）；createStatement 为 null。 */
    private final String preparedSql;
    private final String dataSourceName;
    private final SqlStatRegistry statRegistry;
    private final String traceIdMdcKey;
    /** 绑定参数（按下标排序）；仅 capture-parameters=true 时填充。 */
    private final TreeMap<Integer, String> params = new TreeMap<>();
    /** Statement.addBatch(sql) 收集的语句（已归一化）。 */
    private final List<String> batchSqls = new ArrayList<>();
    /** PreparedStatement.addBatch() 计数。 */
    private int batchCount;

    MonitoringStatementHandler(Statement target, String preparedSql, String dataSourceName,
                               SqlStatRegistry statRegistry, String traceIdMdcKey) {
        this.target = target;
        this.preparedSql = preparedSql == null ? null : SqlStatRegistry.normalizeSql(preparedSql);
        this.dataSourceName = dataSourceName;
        this.statRegistry = statRegistry;
        this.traceIdMdcKey = traceIdMdcKey;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (isExecuteMethod(name)) {
            return executeWithMonitoring(method, args, name);
        }
        // 非执行方法：参数捕获/批处理登记后透传
        captureIfNeeded(name, args);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private static boolean isExecuteMethod(String name) {
        return "execute".equals(name) || "executeQuery".equals(name)
                || "executeUpdate".equals(name) || "executeLargeUpdate".equals(name)
                || "executeBatch".equals(name) || "executeLargeBatch".equals(name);
    }

    /** execute 族统一入口：计时 → 委托目标 → 产出记录（查询结果集再包行数回填代理）。 */
    private Object executeWithMonitoring(Method method, Object[] args, String name) throws Throwable {
        String sql = resolveSql(name, args);
        List<String> paramSnapshot = snapshotParams(name);
        long startEpoch = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        try {
            Object result = method.invoke(target, args);
            long duration = (System.nanoTime() - startNanos) / 1_000_000L;
            long rows = resolveRows(name, result);
            SqlExecutionRecord record = new SqlExecutionRecord(dataSourceName, sql, paramSnapshot,
                    startEpoch, duration, resolveType(name), rows, true, null,
                    currentTraceId(), statRegistry.isSlow(duration));
            statRegistry.record(record);
            if ("executeQuery".equals(name) && result instanceof ResultSet) {
                // 查询：结果集包代理，close 时回填实际取回行数
                return Proxy.newProxyInstance(MonitoringStatementHandler.class.getClassLoader(),
                        new Class<?>[]{ResultSet.class},
                        new MonitoringResultSetHandler((ResultSet) result, record));
            }
            return result;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            long duration = (System.nanoTime() - startNanos) / 1_000_000L;
            // 失败也入统计（errorCount）与明细，随后原样抛出原始异常
            SqlExecutionRecord record = new SqlExecutionRecord(dataSourceName, sql, paramSnapshot,
                    startEpoch, duration, resolveType(name), -1, false,
                    String.valueOf(cause.getMessage()), currentTraceId(), statRegistry.isSlow(duration));
            statRegistry.record(record);
            throw cause;
        }
    }

    /** 非执行方法的旁路处理：参数捕获、批处理登记与清理。 */
    private void captureIfNeeded(String name, Object[] args) {
        if ("clearParameters".equals(name)) {
            params.clear();
            return;
        }
        if ("clearBatch".equals(name)) {
            batchCount = 0;
            batchSqls.clear();
            return;
        }
        if ("addBatch".equals(name)) {
            if (args != null && args.length == 1 && args[0] instanceof String) {
                batchSqls.add(SqlStatRegistry.normalizeSql((String) args[0]));
            } else {
                batchCount++;
            }
            return;
        }
        // setXxx(index, value)：下标为 Integer 的绑定参数捕获（命名参数不采集）
        if (statRegistry.getProperties().isCaptureParameters()
                && name.startsWith("set") && args != null && args.length >= 2
                && args[0] instanceof Integer) {
            if ("setNull".equals(name)) {
                params.put((Integer) args[0], "NULL");
            } else {
                params.put((Integer) args[0], renderParam(args[1]));
            }
        }
    }

    /** 执行 SQL 解析：Statement 变体从入参取，批处理合并批语句，其余用预置 SQL。 */
    private String resolveSql(String name, Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            return SqlStatRegistry.normalizeSql((String) args[0]);
        }
        if (("executeBatch".equals(name) || "executeLargeBatch".equals(name)) && preparedSql == null) {
            return String.join("; ", batchSqls);
        }
        return preparedSql == null ? "" : preparedSql;
    }

    /** 参数快照：批处理不采集（多组参数无法对应单条记录），空参数记 null。 */
    private List<String> snapshotParams(String name) {
        if (params.isEmpty() || name.startsWith("executeBatch") || name.startsWith("executeLargeBatch")
                || !statRegistry.getProperties().isCaptureParameters()) {
            return null;
        }
        return new ArrayList<>(params.values());
    }

    private static String resolveType(String name) {
        if ("executeQuery".equals(name)) {
            return SqlExecutionRecord.TYPE_QUERY;
        }
        if ("executeUpdate".equals(name) || "executeLargeUpdate".equals(name)) {
            return SqlExecutionRecord.TYPE_UPDATE;
        }
        if ("executeBatch".equals(name) || "executeLargeBatch".equals(name)) {
            return SqlExecutionRecord.TYPE_BATCH;
        }
        return SqlExecutionRecord.TYPE_EXECUTE;
    }

    /** 行数解析：更新取返回值、批处理求和、execute 布尔形态取 updateCount、查询待回填。 */
    private long resolveRows(String name, Object result) {
        if (result instanceof Integer || result instanceof Long) {
            return ((Number) result).longValue();
        }
        if (result instanceof int[]) {
            long sum = 0;
            for (int r : (int[]) result) {
                if (r >= 0) {
                    sum += r;
                }
            }
            return sum;
        }
        if (result instanceof long[]) {
            long sum = 0;
            for (long r : (long[]) result) {
                if (r >= 0) {
                    sum += r;
                }
            }
            return sum;
        }
        if (result instanceof Boolean && !((Boolean) result)) {
            // execute() 返回 false = 更新形态，可安全读一次 updateCount
            try {
                return target.getUpdateCount();
            } catch (Exception ignore) {
                return -1;
            }
        }
        return -1;
    }

    /** 参数渲染：String.valueOf + 超长截断。 */
    private static String renderParam(Object value) {
        String text = String.valueOf(value);
        if (text.length() > MAX_PARAM_LENGTH) {
            return text.substring(0, MAX_PARAM_LENGTH) + "...(truncated)";
        }
        return text;
    }

    /** 从 MDC 读取当前 traceId（与链路日志同一 key，无则 null）。 */
    private String currentTraceId() {
        try {
            return MDC.get(traceIdMdcKey);
        } catch (Throwable ignore) {
            // slf4j 异常兜底：监控绝不影响业务执行
            return null;
        }
    }
}
