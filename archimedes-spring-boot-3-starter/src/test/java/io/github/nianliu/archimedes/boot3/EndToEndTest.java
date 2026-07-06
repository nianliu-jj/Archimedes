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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesRestApisAndExcludesFrameworkEndpoints() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/apis", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // 分组结构：无 WebSocket 端点时 webSocketApis 为空数组而非缺失
        Map<String, Object> catalog = mapper.readValue(resp.getBody(),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(catalog).containsKeys("restApis", "webSocketApis", "rpcApis");
        assertThat((List<?>) catalog.get("webSocketApis")).isEmpty();
        assertThat((List<?>) catalog.get("rpcApis")).isEmpty();

        List<Map<String, Object>> apis = restApis(resp.getBody());

        // 用户接口在列
        assertThat(apis).anySatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/demo/hello"));
        // 排除框架 /error 与自身 /archimedes/**
        assertThat(apis).noneSatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/error"));
        assertThat(apis).noneSatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/archimedes/apis"));
    }

    @Test
    void demoEndpointParamIsScanned() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/apis", String.class);
        List<Map<String, Object>> apis = restApis(resp.getBody());

        Map<String, Object> hello = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/demo/hello"))
                .findFirst().orElseThrow();
        assertThat((List<String>) hello.get("httpMethods")).contains("GET");
        List<Map<String, Object>> params = (List<Map<String, Object>>) hello.get("params");
        assertThat(params).anySatisfy(p -> {
            assertThat(p.get("name")).isEqualTo("name");
            assertThat(p.get("source")).isEqualTo("QUERY");
            assertThat(p.get("required")).isEqualTo(true);
        });
    }

    @Test
    void servesUiWithInjectedApiUrl() {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("/archimedes/apis");
        assertThat(resp.getBody()).doesNotContain("__ARCHIMEDES_API_URL__");
        // Slice 12：Tab 化导航（协议分 Tab + REST 在线调试面板）
        assertThat(resp.getBody()).contains("id=\"tabs\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestAndResponseSchemasAreResolved() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/apis", String.class);
        List<Map<String, Object>> apis = restApis(resp.getBody());

        Map<String, Object> create = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/demo/users"))
                .findFirst().orElseThrow();

        // 契约增强：请求体字段树在列（含集合标记）
        Map<String, Object> reqSchema = (Map<String, Object>) create.get("requestBodySchema");
        assertThat(reqSchema.get("type")).isEqualTo("CreateUserRequest");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) reqSchema.get("children");
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.get("name")).isEqualTo("username");
            assertThat(f.get("type")).isEqualTo("String");
        });
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.get("name")).isEqualTo("tags");
            assertThat(f.get("array")).isEqualTo(true);
        });

        // 响应体结构：解包 ResponseEntity 后为同一 DTO
        Map<String, Object> respSchema = (Map<String, Object>) create.get("responseSchema");
        assertThat(respSchema.get("type")).isEqualTo("CreateUserRequest");
    }

    private List<Map<String, Object>> restApis(String body) throws Exception {
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        return (List<Map<String, Object>>) catalog.get("restApis");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
        @Bean
        DemoController demoController() {
            return new DemoController();
        }
    }

    @RestController
    static class DemoController {
        @GetMapping("/demo/hello")
        public String hello(@RequestParam String name) {
            return "hi " + name;
        }

        @PostMapping("/demo/users")
        public ResponseEntity<CreateUserRequest> create(@RequestBody CreateUserRequest request) {
            return ResponseEntity.ok(request);
        }
    }

    /** schema 断言用 DTO：字段反射视图（无需 getter）。 */
    static class CreateUserRequest {
        private String username;
        private List<String> tags;
    }
}
