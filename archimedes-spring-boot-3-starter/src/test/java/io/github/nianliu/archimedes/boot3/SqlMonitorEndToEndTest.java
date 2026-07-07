package io.github.nianliu.archimedes.boot3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL 监控端到端（Servlet 栈 + Boot 默认 Hikari 池 + H2）：真 HTTP 验证
 * /db 端点全结构——Hikari 池指标、统计聚合、慢 SQL（阈值 0 强制全慢）、traceId 关联。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@SpringBootTest(classes = SqlMonitorEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "archimedes.sql.slow-sql-millis=0")
class SqlMonitorEndToEndTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> getDb() throws Exception {
        String body = rest.getForEntity("/archimedes/db", String.class).getBody();
        return mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesPoolMetricsStatsAndSlowSqls() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, item VARCHAR(64))");
        jdbc.update("MERGE INTO orders KEY(id) VALUES (1, 'book')");
        jdbc.queryForList("SELECT * FROM orders");

        Map<String, Object> db = getDb();

        // 阈值透出
        assertThat(db.get("slowSqlMillis")).isEqualTo(0);

        // Boot 默认数据源被包装并输出 Hikari 池指标
        List<Map<String, Object>> dataSources = (List<Map<String, Object>>) db.get("dataSources");
        assertThat(dataSources).hasSize(1);
        assertThat((String) dataSources.get(0).get("targetType")).contains("Hikari");
        Map<String, Object> pool = (Map<String, Object>) dataSources.get(0).get("pool");
        assertThat(pool).isNotNull();
        assertThat(pool.get("type")).isEqualTo("HikariCP");
        assertThat(pool).containsKeys("maximumPoolSize", "totalConnections");

        // 聚合统计包含刚执行的查询
        List<Map<String, Object>> stats = (List<Map<String, Object>>) db.get("sqlStats");
        assertThat(stats).anySatisfy(s -> {
            assertThat((String) s.get("sql")).contains("SELECT * FROM orders");
            assertThat((Integer) s.get("executionCount")).isGreaterThanOrEqualTo(1);
        });

        // 阈值 0：全部执行判慢，慢列表非空且条目带 slow 标记
        List<Map<String, Object>> slow = (List<Map<String, Object>>) db.get("slowSqls");
        assertThat(slow).isNotEmpty();
        assertThat(slow.get(0).get("slow")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void associatesSqlWithRequestTraceId() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, item VARCHAR(64))");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "sql-e2e-trace");
        rest.exchange("/biz/orders", HttpMethod.GET, new HttpEntity<Void>(headers), String.class);

        Map<String, Object> db = getDb();
        List<Map<String, Object>> recent = (List<Map<String, Object>>) db.get("recentSqls");
        // 请求线程内执行的 SQL 携带该请求的 traceId（与链路日志同源）
        assertThat(recent).anySatisfy(r -> assertThat(r.get("traceId")).isEqualTo("sql-e2e-trace"));
    }

    @RestController
    static class OrderController {

        private final JdbcTemplate jdbc;

        OrderController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/biz/orders")
        public List<Map<String, Object>> orders() {
            return jdbc.queryForList("SELECT * FROM orders");
        }
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        OrderController orderController(JdbcTemplate jdbc) {
            return new OrderController(jdbc);
        }
    }
}
