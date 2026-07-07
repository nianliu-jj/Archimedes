package io.github.nianliu.archimedes.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SqlMonitorPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaults() {
        runner.run(context -> {
            SqlMonitorProperties props = context.getBean(SqlMonitorProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getSlowSqlMillis()).isEqualTo(1000);
            assertThat(props.getMaxHistorySize()).isEqualTo(500);
            assertThat(props.isCaptureParameters()).isTrue();
            assertThat(props.getExcludeBeans()).isEmpty();
            assertThat(props.getMaxSqlStats()).isEqualTo(1000);
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                "archimedes.sql.enabled=false",
                "archimedes.sql.slow-sql-millis=200",
                "archimedes.sql.max-history-size=50",
                "archimedes.sql.capture-parameters=false",
                "archimedes.sql.exclude-beans=rawDs,otherDs",
                "archimedes.sql.max-sql-stats=10"
        ).run(context -> {
            SqlMonitorProperties props = context.getBean(SqlMonitorProperties.class);
            assertThat(props.isEnabled()).isFalse();
            assertThat(props.getSlowSqlMillis()).isEqualTo(200);
            assertThat(props.getMaxHistorySize()).isEqualTo(50);
            assertThat(props.isCaptureParameters()).isFalse();
            assertThat(props.getExcludeBeans()).containsExactly("rawDs", "otherDs");
            assertThat(props.getMaxSqlStats()).isEqualTo(10);
        });
    }

    @EnableConfigurationProperties(SqlMonitorProperties.class)
    static class TestConfig {
    }
}
