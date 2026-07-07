package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.jdbc.DataSourceMonitorRegistry;
import io.github.nianliu.archimedes.jdbc.MonitoringDataSource;
import io.github.nianliu.archimedes.jdbc.PoolMetricsContributor;
import io.github.nianliu.archimedes.jdbc.SqlExecutionRecord;
import io.github.nianliu.archimedes.jdbc.SqlMonitorProperties;
import io.github.nianliu.archimedes.jdbc.SqlStatRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesDbControllerTest {

    private final SqlMonitorProperties properties = new SqlMonitorProperties();
    private final SqlStatRegistry statRegistry = new SqlStatRegistry(properties);
    private final DataSourceMonitorRegistry monitorRegistry = new DataSourceMonitorRegistry();

    private MonitoringDataSource newMonitoredDs(String name) {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:ctrl" + System.nanoTime());
        MonitoringDataSource ds = new MonitoringDataSource(h2, name, statRegistry, "traceId");
        monitorRegistry.register(name, ds);
        return ds;
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsFullStructureWithPoolMetrics() {
        newMonitoredDs("primaryDs");
        statRegistry.record(new SqlExecutionRecord("primaryDs", "select 1", null,
                System.currentTimeMillis(), 5, SqlExecutionRecord.TYPE_QUERY,
                1, true, null, "t-1", false));

        PoolMetricsContributor stub = new PoolMetricsContributor() {
            @Override
            public boolean supports(DataSource dataSource) {
                return true;
            }

            @Override
            public Map<String, Object> metrics(DataSource dataSource) {
                return Collections.singletonMap("type", "stub");
            }
        };
        ArchimedesDbController controller = new ArchimedesDbController(
                monitorRegistry, statRegistry, Collections.singletonList(stub));

        Map<String, Object> body = controller.db();

        assertThat(body.get("slowSqlMillis")).isEqualTo(1000L);
        List<Map<String, Object>> dataSources = (List<Map<String, Object>>) body.get("dataSources");
        assertThat(dataSources).hasSize(1);
        assertThat(dataSources.get(0).get("name")).isEqualTo("primaryDs");
        assertThat((String) dataSources.get(0).get("targetType")).contains("JdbcDataSource");
        assertThat((Map<String, Object>) dataSources.get(0).get("pool"))
                .containsEntry("type", "stub");
        assertThat((List<?>) body.get("sqlStats")).hasSize(1);
        assertThat((List<?>) body.get("recentSqls")).hasSize(1);
        assertThat((List<?>) body.get("slowSqls")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void poolIsNullWithoutMatchingContributor() {
        newMonitoredDs("noPoolDs");
        ArchimedesDbController controller = new ArchimedesDbController(
                monitorRegistry, statRegistry, Collections.emptyList());

        List<Map<String, Object>> dataSources =
                (List<Map<String, Object>>) controller.db().get("dataSources");

        assertThat(dataSources.get(0).get("pool")).isNull();
    }
}
