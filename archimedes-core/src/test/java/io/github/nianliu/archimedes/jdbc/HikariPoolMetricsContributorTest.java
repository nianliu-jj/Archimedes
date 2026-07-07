package io.github.nianliu.archimedes.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HikariPoolMetricsContributorTest {

    private final HikariPoolMetricsContributor contributor = new HikariPoolMetricsContributor();

    private HikariDataSource newHikari() {
        HikariDataSource hikari = new HikariDataSource();
        hikari.setJdbcUrl("jdbc:h2:mem:hikariMetrics" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(1);
        return hikari;
    }

    @Test
    void readsRuntimeMetricsAfterPoolStarts() throws Exception {
        try (HikariDataSource hikari = newHikari()) {
            assertThat(contributor.supports(hikari)).isTrue();
            // 触发池启动（第一次连接获取）
            try (Connection ignored = hikari.getConnection()) {
                Map<String, Object> metrics = contributor.metrics(hikari);
                assertThat(metrics.get("type")).isEqualTo("HikariCP");
                assertThat(metrics.get("maximumPoolSize")).isEqualTo(3);
                assertThat(metrics.get("minimumIdle")).isEqualTo(1);
                assertThat((Integer) metrics.get("activeConnections")).isGreaterThanOrEqualTo(1);
                assertThat(metrics).containsKeys("idleConnections",
                        "threadsAwaitingConnection", "totalConnections");
            }
        }
    }

    @Test
    void supportsHikariThroughMonitoringWrapper() {
        try (HikariDataSource hikari = newHikari()) {
            MonitoringDataSource wrapped = new MonitoringDataSource(hikari, "ds",
                    new SqlStatRegistry(new SqlMonitorProperties()), "traceId");
            // 经 Wrapper 链 unwrap 仍能触达原生 Hikari
            assertThat(contributor.supports(wrapped)).isTrue();
            assertThat(contributor.metrics(wrapped).get("type")).isEqualTo("HikariCP");
        }
    }

    @Test
    void rejectsNonHikariDataSource() {
        JdbcDataSource plain = new JdbcDataSource();
        plain.setURL("jdbc:h2:mem:plain;DB_CLOSE_DELAY=-1");
        assertThat(contributor.supports(plain)).isFalse();
    }
}
