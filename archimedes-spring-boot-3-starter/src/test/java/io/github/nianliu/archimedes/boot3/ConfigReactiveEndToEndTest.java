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
 * 配置中心响应式端到端：强制 reactive 启动真实 Netty，验证纯注解式配置端点
 * 在 WebFlux 栈行为与 Servlet 栈一致（查询 + 热更新）。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@SpringBootTest(classes = ConfigReactiveEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=reactive",
                "app.greeting=hello"
        })
class ConfigReactiveEndToEndTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private Environment environment;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configEndpointServesOnReactiveStack() throws Exception {
        String body = rest.getForEntity("/archimedes/config", String.class).getBody();
        Map<String, Object> config = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        assertThat(config.get("hotRefreshEnabled")).isEqualTo(true);
        assertThat(config.get("propertySources")).isNotNull();
    }

    @Test
    void hotUpdateWorksOnReactiveStack() {
        Map<String, String> payload = new HashMap<>();
        payload.put("key", "app.greeting");
        payload.put("value", "hi-reactive");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.postForEntity("/archimedes/config/update",
                new HttpEntity<>(payload, headers), String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hi-reactive");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
