package io.github.nianliu.archimedes.boot2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TraceEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, String> echo(ResponseEntity<String> resp) throws Exception {
        return mapper.readValue(resp.getBody(), new TypeReference<Map<String, String>>() {
        });
    }

    @Test
    void generatesTraceIdIntoMdcAndResponseHeader() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/trace/echo", String.class);

        Map<String, String> body = echo(resp);
        assertThat(body.get("traceId")).isNotBlank();
        assertThat(body.get("spanId")).isNotBlank();
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isEqualTo(body.get("traceId"));
    }

    @Test
    void propagatesIncomingHeader() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "abc123");
        ResponseEntity<String> resp = rest.exchange("/trace/echo", HttpMethod.GET,
                new HttpEntity<Void>(headers), String.class);

        assertThat(echo(resp).get("traceId")).isEqualTo("abc123");
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isEqualTo("abc123");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @RestController
        static class TraceEchoController {
            @GetMapping("/trace/echo")
            public Map<String, String> echo() {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("traceId", MDC.get("traceId"));
                body.put("spanId", MDC.get("spanId"));
                return body;
            }
        }
    }
}
