package io.github.nianliu.archimedes.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 真实链路回环：经监控数据源执行 DDL/DML/查询/批处理/异常 SQL，
 * 验证明细记录（类型/行数/参数/归一化/traceId/异常）与统计聚合全链路正确。
 */
class MonitoringDataSourceH2Test {

    private final SqlMonitorProperties properties = new SqlMonitorProperties();
    private final SqlStatRegistry registry = new SqlStatRegistry(properties);
    private MonitoringDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource h2 = new JdbcDataSource();
        // 每个测试独立的私有内存库，用后即弃
        h2.setURL("jdbc:h2:mem:" + getClass().getSimpleName() + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1");
        dataSource = new MonitoringDataSource(h2, "testDs", registry, "traceId");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE demo (id INT PRIMARY KEY, name VARCHAR(64))");
        }
    }

    @AfterEach
    void cleanMdc() {
        MDC.remove("traceId");
    }

    private SqlExecutionRecord lastRecord() {
        return registry.recentSnapshot().get(0);
    }

    @Test
    void recordsUpdateWithAffectedRowsAndParams() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO demo VALUES (?, ?)")) {
            ps.setInt(1, 1);
            ps.setString(2, "alice");
            int affected = ps.executeUpdate();
            assertThat(affected).isEqualTo(1);
        }

        SqlExecutionRecord record = lastRecord();
        assertThat(record.getType()).isEqualTo(SqlExecutionRecord.TYPE_UPDATE);
        assertThat(record.getRows()).isEqualTo(1);
        assertThat(record.isSuccess()).isTrue();
        assertThat(record.getSql()).isEqualTo("INSERT INTO demo VALUES (?, ?)");
        assertThat(record.getParams()).containsExactly("1", "alice");
    }

    @Test
    void countsFetchedRowsForQueryOnResultSetClose() throws SQLException {
        insertRows(3);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM demo");
             ResultSet rs = ps.executeQuery()) {
            int seen = 0;
            while (rs.next()) {
                seen++;
            }
            assertThat(seen).isEqualTo(3);
        }

        SqlExecutionRecord record = registry.recentSnapshot().stream()
                .filter(r -> r.getType().equals(SqlExecutionRecord.TYPE_QUERY))
                .findFirst().orElseThrow();
        // ResultSet close 后行数已回填
        assertThat(record.getRows()).isEqualTo(3);
    }

    @Test
    void normalizesLiteralStatementSql() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT   INTO demo\n VALUES (9,   'bob')");
        }

        assertThat(lastRecord().getSql()).isEqualTo("INSERT INTO demo VALUES (9, 'bob')");
    }

    @Test
    void recordsBatchWithSummedRows() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO demo VALUES (?, ?)")) {
            ps.setInt(1, 10);
            ps.setString(2, "a");
            ps.addBatch();
            ps.setInt(1, 11);
            ps.setString(2, "b");
            ps.addBatch();
            int[] result = ps.executeBatch();
            assertThat(result).hasSize(2);
        }

        SqlExecutionRecord record = lastRecord();
        assertThat(record.getType()).isEqualTo(SqlExecutionRecord.TYPE_BATCH);
        assertThat(record.getRows()).isEqualTo(2);
        // 批处理不采集参数（多组参数无法对应单条记录）
        assertThat(record.getParams()).isNull();
    }

    @Test
    void recordsFailureAndRethrowsOriginalException() {
        assertThatThrownBy(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO not_exist VALUES (1)");
            }
        }).isInstanceOf(SQLException.class);

        SqlExecutionRecord record = lastRecord();
        assertThat(record.isSuccess()).isFalse();
        assertThat(record.getErrorMessage()).isNotBlank();
        // 失败计入聚合 errorCount
        assertThat(registry.statViews().stream()
                .filter(v -> String.valueOf(v.get("sql")).contains("not_exist"))
                .findFirst().orElseThrow()
                .get("errorCount")).isEqualTo(1L);
    }

    @Test
    void associatesTraceIdFromMdc() throws SQLException {
        MDC.put("traceId", "sql-trace-1");
        insertRows(1);

        assertThat(lastRecord().getTraceId()).isEqualTo("sql-trace-1");
    }

    @Test
    void skipsParameterCaptureWhenDisabled() throws SQLException {
        properties.setCaptureParameters(false);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO demo VALUES (?, ?)")) {
            ps.setInt(1, 20);
            ps.setString(2, "secret");
            ps.executeUpdate();
        }

        assertThat(lastRecord().getParams()).isNull();
    }

    @Test
    void unwrapReachesTargetThroughWrapperChain() throws SQLException {
        assertThat(dataSource.isWrapperFor(JdbcDataSource.class)).isTrue();
        assertThat(dataSource.unwrap(JdbcDataSource.class)).isInstanceOf(JdbcDataSource.class);
        assertThat(dataSource.unwrap(MonitoringDataSource.class)).isSameAs(dataSource);
    }

    @Test
    void marksSlowWhenThresholdIsZero() throws SQLException {
        properties.setSlowSqlMillis(0);
        insertRows(1);

        assertThat(lastRecord().isSlow()).isTrue();
        assertThat(registry.slowSnapshot()).isNotEmpty();
    }

    private void insertRows(int count) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO demo VALUES (?, ?)")) {
            for (int i = 0; i < count; i++) {
                ps.setInt(1, 100 + i);
                ps.setString(2, "row" + i);
                ps.executeUpdate();
            }
        }
    }
}
