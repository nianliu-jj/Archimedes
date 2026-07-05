package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.trace.propagation.MdcWrappers;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三路跨线程传递端到端：@Async 默认池、自定义 ExecutorService Bean、commonPool + MdcWrappers。
 */
@SpringBootTest(classes = PropagationEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PropagationEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private String get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "prop-trace-1");
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<Void>(headers), String.class).getBody();
    }

    @Test
    void asyncMethodCarriesTraceId() {
        assertThat(get("/prop/async")).isEqualTo("prop-trace-1");
    }

    @Test
    void customExecutorServiceBeanCarriesTraceId() {
        assertThat(get("/prop/pool")).isEqualTo("prop-trace-1");
    }

    @Test
    void commonPoolWithManualWrapCarriesTraceId() {
        assertThat(get("/prop/manual")).isEqualTo("prop-trace-1");
    }

    @EnableAsync
    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        /**
         * 显式 taskExecutor：自定义 ExecutorService Bean 会让 Boot 的 applicationTaskExecutor
         * 退避（@ConditionalOnMissingBean(Executor.class)），@Async 将落到非 Bean 的
         * SimpleAsyncTaskExecutor。这里同时端到端覆盖 TPTE 注入 decorator 的路径。
         */
        @Bean
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor() {
            org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                    new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            return executor;
        }

        @Bean(destroyMethod = "shutdown")
        ExecutorService customPool() {
            return Executors.newFixedThreadPool(2);
        }

        @Bean
        AsyncService asyncService() {
            return new AsyncService();
        }

        static class AsyncService {
            @Async
            public CompletableFuture<String> traceIdInAsync() {
                return CompletableFuture.completedFuture(String.valueOf(MDC.get("traceId")));
            }
        }

        @RestController
        static class PropController {

            private final AsyncService asyncService;
            private final ExecutorService customPool;

            PropController(AsyncService asyncService, ExecutorService customPool) {
                this.asyncService = asyncService;
                this.customPool = customPool;
            }

            @GetMapping("/prop/async")
            public String viaAsync() {
                return asyncService.traceIdInAsync().join();
            }

            @GetMapping("/prop/pool")
            public String viaPool() throws Exception {
                return String.valueOf(customPool.submit(() -> MDC.get("traceId")).get());
            }

            @GetMapping("/prop/manual")
            public String viaCommonPoolWithWrap() {
                return CompletableFuture
                        .supplyAsync(MdcWrappers.wrap((java.util.function.Supplier<String>) () ->
                                String.valueOf(MDC.get("traceId"))))
                        .join();
            }
        }
    }
}
