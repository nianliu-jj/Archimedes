package io.github.nianliu.archimedes.exampleall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 线程池装配：验证两条跨线程 traceId 自动传递路径。
 *
 * <p>重要经验：启用 STOMP 后其内部通道执行器成为容器 Executor Bean，
 * Spring Boot 的 applicationTaskExecutor 会因 @ConditionalOnMissingBean(Executor.class)
 * 退避，@Async 将退化为非容器管理的 SimpleAsyncTaskExecutor（无法自动传递，
 * Archimedes 启动时会打 WARN）。显式定义名为 taskExecutor 的 Bean 即回到覆盖范围。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Configuration
public class ExecutorConfig {

    /** @Async 缺省线程池：Archimedes 自动注入 MdcTaskDecorator（Bean 类型不变） */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("all-async-");
        return executor;
    }

    /** 业务自定义 ExecutorService Bean：Archimedes 的 BeanPostProcessor 自动替换为 MDC 传递包装器 */
    @Bean
    public ExecutorService bizPool() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("all-bizpool-" + thread.getId());
            return thread;
        });
    }
}
