package io.github.nianliu.archimedes.example.boot2.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 跨线程 traceId 传递演示（javax 世界）：@Async 方法内的日志与 MDC 与请求线程同 traceId。
 */
@Service
public class AsyncDemoService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDemoService.class);

    @Async
    public CompletableFuture<String> traceIdInAsyncThread() {
        log.info("async task running, traceId={}", MDC.get("traceId"));
        return CompletableFuture.completedFuture(String.valueOf(MDC.get("traceId")));
    }

    @Configuration
    @EnableAsync
    public static class AsyncConfig {

        /**
         * 显式 taskExecutor：本应用启用 STOMP 后，其内部通道执行器 Bean 会让 Boot 的
         * applicationTaskExecutor 退避，@Async 将退化为非容器管理的 SimpleAsyncTaskExecutor
         * （MDC 自动传递覆盖不到）。定义名为 taskExecutor 的 Bean 让 @Async 回到容器管理的池。
         */
        @Bean
        public ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(4);
            executor.setThreadNamePrefix("demo-async-");
            return executor;
        }
    }
}
