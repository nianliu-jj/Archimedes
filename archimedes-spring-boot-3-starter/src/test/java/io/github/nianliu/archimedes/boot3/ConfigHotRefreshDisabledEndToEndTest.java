package io.github.nianliu.archimedes.boot3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 热更新开关关闭端到端：查询仍可用（并如实报告开关状态），update 端点返回 403
 * 且不产生任何变更。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@SpringBootTest(classes = ConfigHotRefreshDisabledEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.greeting=hello",
                "archimedes.config.hot-refresh-enabled=false"
        })
class ConfigHotRefreshDisabledEndToEndTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private Environment environment;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void queryStillWorksAndReportsDisabledFlag() throws Exception {
        String body = rest.getForEntity("/archimedes/config", String.class).getBody();
        Map<String, Object> config = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        assertThat(config.get("hotRefreshEnabled")).isEqualTo(false);
    }

    @Test
    void updateRejectedWith403AndNothingChanges() {
        Map<String, String> payload = new HashMap<>();
        payload.put("key", "app.greeting");
        payload.put("value", "hi");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.postForEntity("/archimedes/config/update",
                new HttpEntity<>(payload, headers), String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hello");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
