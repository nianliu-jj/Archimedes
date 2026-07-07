package io.github.nianliu.archimedes.boot2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置中心端到端（SB2 javax 栈）：镜像 SB3 servlet 用例——真 HTTP 验证
 * 全量查询/敏感脱敏/热更新覆盖/@ConfigurationProperties 重绑定/删除恢复闭环。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@SpringBootTest(classes = ConfigEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.greeting=hello",
                "demo.password=123456",
                "demo.conf.title=v1"
        })
class ConfigEndToEndTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private Environment environment;
    @Autowired
    private DemoConf demoConf;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> getConfig() throws Exception {
        String body = rest.getForEntity("/archimedes/config", String.class).getBody();
        return mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
    }

    private ResponseEntity<String> postUpdate(Map<String, String> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/archimedes/config/update",
                new HttpEntity<Map<String, String>>(payload, headers), String.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsAllPropertySourcesWithMasking() throws Exception {
        Map<String, Object> config = getConfig();

        assertThat(config.get("hotRefreshEnabled")).isEqualTo(true);
        List<Map<String, Object>> sources = (List<Map<String, Object>>) config.get("propertySources");
        assertThat(sources).isNotEmpty();

        Map<String, Object> greeting = findEntry(sources, "app.greeting");
        assertThat(greeting.get("value")).isEqualTo("hello");

        // 敏感键脱敏且带标记
        Map<String, Object> password = findEntry(sources, "demo.password");
        assertThat(password.get("value")).isEqualTo("******");
        assertThat(password.get("sensitive")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hotUpdateOverridesRebindsAndRestores() throws Exception {
        // 1) 写入覆盖：Environment 立即生效 + @ConfigurationProperties Bean 原地重绑定
        Map<String, String> payload = new HashMap<>();
        payload.put("key", "demo.conf.title");
        payload.put("value", "v2");
        ResponseEntity<String> updateResp = postUpdate(payload);
        assertThat(updateResp.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> updateBody = mapper.readValue(updateResp.getBody(),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(updateBody.get("oldValue")).isEqualTo("v1");
        assertThat(updateBody.get("newValue")).isEqualTo("v2");
        assertThat((List<String>) updateBody.get("refreshedBeans")).isNotEmpty();

        assertThat(environment.getProperty("demo.conf.title")).isEqualTo("v2");
        assertThat(demoConf.getTitle()).isEqualTo("v2");

        // 2) 查询端点报告动态覆盖键
        Map<String, Object> config = getConfig();
        assertThat((List<String>) config.get("dynamicKeys")).contains("demo.conf.title");

        // 3) 删除覆盖：恢复底层原值并再次重绑定
        Map<String, String> removal = new HashMap<>();
        removal.put("key", "demo.conf.title");
        ResponseEntity<String> removeResp = postUpdate(removal);
        Map<String, Object> removeBody = mapper.readValue(removeResp.getBody(),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(removeBody.get("removed")).isEqualTo(true);
        assertThat(environment.getProperty("demo.conf.title")).isEqualTo("v1");
        assertThat(demoConf.getTitle()).isEqualTo("v1");
    }

    @Test
    void rejectsBlankKeyWith400() {
        ResponseEntity<String> resp = postUpdate(new HashMap<String, String>());
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findEntry(List<Map<String, Object>> sources, String key) {
        for (Map<String, Object> source : sources) {
            for (Map<String, Object> entry : (List<Map<String, Object>>) source.get("entries")) {
                if (key.equals(entry.get("key"))) {
                    return entry;
                }
            }
        }
        throw new AssertionError("config entry not found: " + key);
    }

    @ConfigurationProperties(prefix = "demo.conf")
    static class DemoConf {
        private String title = "default";

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @EnableAutoConfiguration
    @EnableConfigurationProperties(DemoConf.class)
    @Configuration
    static class TestApp {
    }
}
