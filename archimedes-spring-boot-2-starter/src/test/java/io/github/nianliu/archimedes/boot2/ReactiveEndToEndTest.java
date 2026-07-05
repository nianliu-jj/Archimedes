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
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebFlux 端到端（SB2 侧）：强制 reactive 启动真实 Netty 服务，验证响应式
 * {@code @RestController} 契约在列、自身端点排除、UI 页可达。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@SpringBootTest(classes = ReactiveEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive")
class ReactiveEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void scansReactiveRestControllers() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> apis = (List<Map<String, Object>>) catalog.get("restApis");

        // 响应式端点在列，返回类型为 Mono 签名
        assertThat(apis).anySatisfy(api -> {
            assertThat((List<String>) api.get("paths")).contains("/reactive/greet");
            assertThat((List<String>) api.get("httpMethods")).contains("GET");
            assertThat((String) api.get("returnType")).startsWith("reactor.core.publisher.Mono");
        });
        // 自身端点排除
        assertThat(apis).noneSatisfy(api ->
                assertThat((List<String>) api.get("paths")).contains("/archimedes/apis"));
        // 分组结构完整（RPC/WS 字段存在且为数组）
        assertThat(catalog.get("webSocketApis")).isInstanceOf(List.class);
        assertThat(catalog.get("rpcApis")).isInstanceOf(List.class);
    }

    @Test
    void reactiveEndpointActuallyServes() {
        ResponseEntity<String> resp = rest.getForEntity("/reactive/greet?name=flux", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isEqualTo("hello flux");
    }

    @Test
    void servesUiOnReactiveStack() {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).contains("/archimedes/apis");
        assertThat(resp.getBody()).doesNotContain("__ARCHIMEDES_API_URL__");
        assertThat(resp.getBody()).contains("id=\"tabs\"");
    }

    @RestController
    static class ReactiveGreetingController {

        @GetMapping("/reactive/greet")
        public Mono<String> greet(@RequestParam("name") String name) {
            return Mono.just("hello " + name);
        }
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        ReactiveGreetingController reactiveGreetingController() {
            return new ReactiveGreetingController();
        }
    }
}
