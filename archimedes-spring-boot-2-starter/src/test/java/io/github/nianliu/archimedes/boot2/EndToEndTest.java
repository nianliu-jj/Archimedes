package io.github.nianliu.archimedes.boot2;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 与 boot3 侧 EndToEndTest 镜像：相同的样例 controller 与相同的期望 JSON 语义，
 * 断言双端扫描行为一致。源码保持 Java 8 语法（本模块 release=8）。
 */
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

        List<Map<String, Object>> apis = mapper.readValue(resp.getBody(),
                new TypeReference<List<Map<String, Object>>>() {
                });

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
        List<Map<String, Object>> apis = mapper.readValue(resp.getBody(),
                new TypeReference<List<Map<String, Object>>>() {
                });

        Map<String, Object> hello = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/demo/hello"))
                .findFirst().orElseThrow(IllegalStateException::new);
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
    }
}
