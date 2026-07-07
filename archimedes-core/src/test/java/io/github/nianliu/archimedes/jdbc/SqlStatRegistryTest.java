package io.github.nianliu.archimedes.jdbc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqlStatRegistryTest {

    private final SqlMonitorProperties properties = new SqlMonitorProperties();
    private final SqlStatRegistry registry = new SqlStatRegistry(properties);

    private SqlExecutionRecord record(String sql, long duration, boolean success) {
        return new SqlExecutionRecord("ds", sql, null, System.currentTimeMillis(), duration,
                SqlExecutionRecord.TYPE_QUERY, 1, success, success ? null : "boom",
                null, registry.isSlow(duration));
    }

    @Test
    void normalizesWhitespace() {
        assertThat(SqlStatRegistry.normalizeSql("  select *\n  from   t\twhere id = ?  "))
                .isEqualTo("select * from t where id = ?");
        assertThat(SqlStatRegistry.normalizeSql(null)).isEmpty();
    }

    @Test
    void aggregatesSameSqlIncludingErrors() {
        registry.record(record("select 1", 10, true));
        registry.record(record("select 1", 30, true));
        registry.record(record("select 1", 20, false));

        List<Map<String, Object>> views = registry.statViews();
        assertThat(views).hasSize(1);
        Map<String, Object> view = views.get(0);
        assertThat(view.get("executionCount")).isEqualTo(3L);
        assertThat(view.get("totalMillis")).isEqualTo(60L);
        assertThat(view.get("avgMillis")).isEqualTo(20L);
        assertThat(view.get("maxMillis")).isEqualTo(30L);
        assertThat(view.get("errorCount")).isEqualTo(1L);
    }

    @Test
    void marksSlowByThreshold() {
        properties.setSlowSqlMillis(100);
        registry.record(record("fast", 99, true));
        registry.record(record("slow", 100, true));

        assertThat(registry.slowSnapshot()).hasSize(1);
        assertThat(registry.slowSnapshot().get(0).getSql()).isEqualTo("slow");
        assertThat(registry.recentSnapshot()).hasSize(2);
        // 快照最新在前
        assertThat(registry.recentSnapshot().get(0).getSql()).isEqualTo("slow");
    }

    @Test
    void evictsOldestWhenHistoryExceedsLimit() {
        properties.setMaxHistorySize(3);
        for (int i = 1; i <= 5; i++) {
            registry.record(record("sql-" + i, 1, true));
        }

        List<SqlExecutionRecord> recent = registry.recentSnapshot();
        assertThat(recent).hasSize(3);
        // 最老的 sql-1/sql-2 被逐出，最新在前
        assertThat(recent.get(0).getSql()).isEqualTo("sql-5");
        assertThat(recent.get(2).getSql()).isEqualTo("sql-3");
    }

    @Test
    void stopsCreatingStatSlotsAtCap() {
        properties.setMaxSqlStats(2);
        registry.record(record("a", 1, true));
        registry.record(record("b", 1, true));
        registry.record(record("c", 1, true)); // 超上限：只记明细不聚合

        assertThat(registry.statViews()).hasSize(2);
        assertThat(registry.recentSnapshot()).hasSize(3);
        // 已有槽继续累加不受影响
        registry.record(record("a", 1, true));
        assertThat(registry.statViews().stream()
                .filter(v -> v.get("sql").equals("a"))
                .findFirst().orElseThrow()
                .get("executionCount")).isEqualTo(2L);
    }

    @Test
    void sortsStatsByTotalMillisDesc() {
        registry.record(record("cheap", 5, true));
        registry.record(record("expensive", 500, true));

        List<Map<String, Object>> views = registry.statViews();
        assertThat(views.get(0).get("sql")).isEqualTo("expensive");
    }
}
