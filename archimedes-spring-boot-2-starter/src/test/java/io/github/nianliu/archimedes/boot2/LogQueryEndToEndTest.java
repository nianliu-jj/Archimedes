package io.github.nianliu.archimedes.boot2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采集-查询闭环端到端：请求线程 + @Async 线程各打一条日志 → 按 traceId 查询归集两条且线程不同。
 */
@SpringBootTest(classes = LogQueryEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogQueryEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> query(String traceId) throws Exception {
        String body = rest.getForEntity("/archimedes/logs/trace/" + traceId, String.class).getBody();
        return mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectsLogsFromBothThreadsUnderOneTraceId() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "logq-e2e-1");
        rest.exchange("/biz/work", HttpMethod.GET, new HttpEntity<Void>(headers), String.class);

        Map<String, Object> result = query("logq-e2e-1");
        assertThat((Integer) result.get("total")).isGreaterThanOrEqualTo(2);

        List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        List<String> messages = logs.stream().map(l -> (String) l.get("message")).collect(Collectors.toList());
        assertThat(messages).contains("work on request thread", "work on async thread");

        String requestThread = threadOf(logs, "work on request thread");
        String asyncThread = threadOf(logs, "work on async thread");
        assertThat(requestThread).isNotEqualTo(asyncThread);
    }

    @Test
    @SuppressWarnings("unchecked")
    void paginationAndOrderingWork() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "logq-e2e-2");
        rest.exchange("/biz/burst", HttpMethod.GET, new HttpEntity<Void>(headers), String.class);

        String body = rest.getForEntity("/archimedes/logs/trace/logq-e2e-2?page=1&size=3", String.class).getBody();
        Map<String, Object> result = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        assertThat((Integer) result.get("total")).isEqualTo(5);
        List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        assertThat(logs).hasSize(3);
        assertThat((String) logs.get(0).get("message")).isEqualTo("burst 1");
    }

    @Test
    void currentTraceIdEndpointEchoesHeader() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "logq-current");
        String body = rest.exchange("/archimedes/trace/current", HttpMethod.GET,
                new HttpEntity<Void>(headers), String.class).getBody();

        Map<String, Object> result = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        assertThat(result.get("traceId")).isEqualTo("logq-current");
    }

    @SuppressWarnings("unchecked")
    private String threadOf(List<Map<String, Object>> logs, String message) {
        return logs.stream().filter(l -> message.equals(l.get("message")))
                .map(l -> (String) l.get("thread")).findFirst().orElse(null);
    }

    @EnableAsync
    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        WorkService workService() {
            return new WorkService();
        }

        static class WorkService {
            private static final Logger log = LoggerFactory.getLogger(WorkService.class);

            @Async
            public CompletableFuture<Void> asyncWork() {
                log.info("work on async thread");
                return CompletableFuture.completedFuture(null);
            }
        }

        @RestController
        static class BizController {
            private static final Logger log = LoggerFactory.getLogger(BizController.class);

            private final WorkService workService;

            BizController(WorkService workService) {
                this.workService = workService;
            }

            @GetMapping("/biz/work")
            public String work() {
                log.info("work on request thread");
                workService.asyncWork().join();
                return "ok:" + MDC.get("traceId");
            }

            @GetMapping("/biz/burst")
            public String burst() {
                for (int i = 1; i <= 5; i++) {
                    log.info("burst {}", i);
                }
                return "ok";
            }
        }
    }
}
