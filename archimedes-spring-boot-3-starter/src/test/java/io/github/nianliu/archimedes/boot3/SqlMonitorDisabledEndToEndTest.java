package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.jdbc.MonitoringDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 监控关闭端到端：archimedes.sql.enabled=false 时数据源不包装、/db 端点 404。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@SpringBootTest(classes = SqlMonitorDisabledEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "archimedes.sql.enabled=false")
class SqlMonitorDisabledEndToEndTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private DataSource dataSource;

    @Test
    void dataSourceNotWrappedAndEndpointReturns404() {
        assertThat(dataSource).isNotInstanceOf(MonitoringDataSource.class);
        assertThat(rest.getForEntity("/archimedes/db", String.class).getStatusCode().value())
                .isEqualTo(404);
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
