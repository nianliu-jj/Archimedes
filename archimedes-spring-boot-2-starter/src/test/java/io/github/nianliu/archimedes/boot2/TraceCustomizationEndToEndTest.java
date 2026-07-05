package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.trace.TraceIdGenerator;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TraceCustomizationEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "archimedes.trace.header-name=X-Request-Id")
class TraceCustomizationEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void customGeneratorAndHeaderNameUsed() {
        ResponseEntity<String> resp = rest.getForEntity("/trace/plain", String.class);

        assertThat(resp.getBody()).isEqualTo("fixed-42");
        assertThat(resp.getHeaders().getFirst("X-Request-Id")).isEqualTo("fixed-42");
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNull();
    }

    @Test
    void incomingCustomHeaderWinsOverGenerator() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "req-777");
        ResponseEntity<String> resp = rest.exchange("/trace/plain", HttpMethod.GET,
                new HttpEntity<Void>(headers), String.class);

        assertThat(resp.getBody()).isEqualTo("req-777");
        assertThat(resp.getHeaders().getFirst("X-Request-Id")).isEqualTo("req-777");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        TraceIdGenerator fixedGenerator() {
            return new TraceIdGenerator() {
                @Override
                public String generate() {
                    return "fixed-42";
                }
            };
        }

        @RestController
        static class PlainController {
            @GetMapping("/trace/plain")
            public String plain() {
                return MDC.get("traceId");
            }
        }
    }
}
