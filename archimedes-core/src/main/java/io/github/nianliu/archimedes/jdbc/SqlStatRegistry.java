package io.github.nianliu.archimedes.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/**
 * SQL 统计注册表：执行明细的唯一汇聚点。
 * <ul>
 *   <li>聚合：按「数据源 + 归一化 SQL」维度无锁累加次数/总耗时/最大耗时/失败数；</li>
 *   <li>明细：最近执行与慢 SQL 两个有界双端队列（超限逐出最老）；</li>
 *   <li>防膨胀：去重 SQL 聚合槽达到 max-sql-stats 后不再新建（明细仍记录），只告警一次。</li>
 * </ul>
 * 热点路径（record）临界区极小：聚合走 ConcurrentHashMap + LongAdder，入队仅 addLast+超限逐出。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class SqlStatRegistry {

    private static final Logger log = LoggerFactory.getLogger(SqlStatRegistry.class);

    /** SQL 空白归一化（换行/制表/连续空格折叠为单空格），使同一语句的不同排版聚合到同一槽。 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SqlMonitorProperties properties;
    /** 聚合槽：key = dataSource + '|' + normalizedSql。 */
    private final ConcurrentHashMap<String, SqlStat> stats = new ConcurrentHashMap<>();
    /** 最近执行明细（有界，最老先出）。 */
    private final Deque<SqlExecutionRecord> recent = new ArrayDeque<>();
    /** 慢 SQL 明细（有界，最老先出）。 */
    private final Deque<SqlExecutionRecord> slow = new ArrayDeque<>();
    /** 聚合槽达到上限只告警一次的闸门。 */
    private final AtomicBoolean statCapWarned = new AtomicBoolean();

    public SqlStatRegistry(SqlMonitorProperties properties) {
        this.properties = properties;
    }

    public SqlMonitorProperties getProperties() {
        return properties;
    }

    /** SQL 空白归一化：trim + 连续空白折叠为单空格。 */
    public static String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return WHITESPACE.matcher(sql.trim()).replaceAll(" ");
    }

    /** 是否慢 SQL：耗时 >= 阈值（阈值配 0 即全量判慢，便于联调）。 */
    public boolean isSlow(long durationMillis) {
        return durationMillis >= properties.getSlowSqlMillis();
    }

    /** 记录一次执行：聚合累加 + 明细入队（慢 SQL 双入队）。 */
    public void record(SqlExecutionRecord record) {
        aggregate(record);
        offerBounded(recent, record);
        if (record.isSlow()) {
            offerBounded(slow, record);
        }
        if (log.isDebugEnabled()) {
            log.debug("SQL 监控记录: ds={} type={} 耗时={}ms slow={} sql={}",
                    record.getDataSource(), record.getType(), record.getDurationMillis(),
                    record.isSlow(), record.getSql());
        }
    }

    /** 聚合累加：达到 max-sql-stats 上限后新 SQL 不再建槽（明细仍会记录）。 */
    private void aggregate(SqlExecutionRecord record) {
        String key = record.getDataSource() + "|" + record.getSql();
        SqlStat stat = stats.get(key);
        if (stat == null) {
            if (stats.size() >= properties.getMaxSqlStats()) {
                if (statCapWarned.compareAndSet(false, true)) {
                    log.warn("去重 SQL 聚合条目已达上限 {}（archimedes.sql.max-sql-stats），"
                            + "新 SQL 只记录明细不再聚合", properties.getMaxSqlStats());
                }
                return;
            }
            stat = stats.computeIfAbsent(key,
                    k -> new SqlStat(record.getDataSource(), record.getSql()));
        }
        stat.accumulate(record);
    }

    /** 有界入队：超限逐出最老（单条临界区极小）。 */
    private void offerBounded(Deque<SqlExecutionRecord> deque, SqlExecutionRecord record) {
        int max = Math.max(1, properties.getMaxHistorySize());
        synchronized (deque) {
            deque.addLast(record);
            while (deque.size() > max) {
                deque.removeFirst();
            }
        }
    }

    /** 聚合统计快照（按总耗时降序——最贵的 SQL 排最前）。 */
    public List<Map<String, Object>> statViews() {
        List<SqlStat> snapshot = new ArrayList<>(stats.values());
        snapshot.sort(Comparator.comparingLong((SqlStat s) -> s.totalMillis.sum()).reversed());
        List<Map<String, Object>> views = new ArrayList<>(snapshot.size());
        for (SqlStat stat : snapshot) {
            views.add(stat.view());
        }
        return views;
    }

    /** 最近执行快照（最新在前，便于前端直接展示）。 */
    public List<SqlExecutionRecord> recentSnapshot() {
        return snapshotNewestFirst(recent);
    }

    /** 慢 SQL 快照（最新在前）。 */
    public List<SqlExecutionRecord> slowSnapshot() {
        return snapshotNewestFirst(slow);
    }

    private List<SqlExecutionRecord> snapshotNewestFirst(Deque<SqlExecutionRecord> deque) {
        synchronized (deque) {
            List<SqlExecutionRecord> list = new ArrayList<>(deque.size());
            Iterator<SqlExecutionRecord> it = deque.descendingIterator();
            while (it.hasNext()) {
                list.add(it.next());
            }
            return list;
        }
    }

    /** 单条 SQL 的聚合槽：全部无锁累加器，view() 时快照计算平均值。 */
    static class SqlStat {
        final String dataSource;
        final String sql;
        final LongAdder executionCount = new LongAdder();
        final LongAdder errorCount = new LongAdder();
        final LongAdder totalMillis = new LongAdder();
        final AtomicLong maxMillis = new AtomicLong();
        volatile long lastExecutedAt;

        SqlStat(String dataSource, String sql) {
            this.dataSource = dataSource;
            this.sql = sql;
        }

        void accumulate(SqlExecutionRecord record) {
            executionCount.increment();
            totalMillis.add(record.getDurationMillis());
            maxMillis.accumulateAndGet(record.getDurationMillis(), Math::max);
            if (!record.isSuccess()) {
                errorCount.increment();
            }
            lastExecutedAt = record.getStartTime();
        }

        Map<String, Object> view() {
            long count = executionCount.sum();
            long total = totalMillis.sum();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("dataSource", dataSource);
            view.put("sql", sql);
            view.put("executionCount", count);
            view.put("totalMillis", total);
            view.put("avgMillis", count == 0 ? 0 : total / count);
            view.put("maxMillis", maxMillis.get());
            view.put("errorCount", errorCount.sum());
            view.put("lastExecutedAt", lastExecutedAt);
            return view;
        }
    }
}
