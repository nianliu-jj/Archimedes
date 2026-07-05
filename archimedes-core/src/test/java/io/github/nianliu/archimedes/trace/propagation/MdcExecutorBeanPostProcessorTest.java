package io.github.nianliu.archimedes.trace.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MdcExecutorBeanPostProcessorTest {

    private final MdcExecutorBeanPostProcessor bpp =
            new MdcExecutorBeanPostProcessor(Collections.singletonList("excludedPool"));

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void wrapsExecutorServiceBeanPreservingInterface() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        try {
            Object processed = bpp.postProcessAfterInitialization(raw, "myPool");

            assertThat(processed).isNotSameAs(raw).isInstanceOf(ExecutorService.class);
            MDC.put("traceId", "bpp-trace");
            assertThat(((ExecutorService) processed).submit(() -> MDC.get("traceId")).get())
                    .isEqualTo("bpp-trace");
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    void wrapsScheduledExecutorServicePreservingInterface() {
        ScheduledExecutorService raw = Executors.newSingleThreadScheduledExecutor();
        try {
            Object processed = bpp.postProcessAfterInitialization(raw, "myScheduled");
            assertThat(processed).isNotSameAs(raw).isInstanceOf(ScheduledExecutorService.class);
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    void skipsExcludedBeanAndTaskScheduler() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        try {
            assertThat(bpp.postProcessAfterInitialization(raw, "excludedPool")).isSameAs(raw);
            assertThat(bpp.postProcessAfterInitialization(scheduler, "myScheduler")).isSameAs(scheduler);
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    void wrapIsIdempotentAcrossPostProcessing() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        try {
            Object once = bpp.postProcessAfterInitialization(raw, "myPool");
            Object twice = bpp.postProcessAfterInitialization(once, "myPool");
            assertThat(twice).isSameAs(once);
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    void injectsDecoratorIntoThreadPoolTaskExecutor() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        bpp.postProcessBeforeInitialization(executor, "tpte");
        executor.initialize();
        try {
            assertThat(bpp.postProcessAfterInitialization(executor, "tpte")).isSameAs(executor);

            MDC.put("traceId", "tpte-trace");
            assertThat(executor.submit(() -> MDC.get("traceId")).get()).isEqualTo("tpte-trace");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void composesWithUserDecorator() throws Exception {
        AtomicBoolean userDecoratorApplied = new AtomicBoolean(false);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new TaskDecorator() {
            @Override
            public Runnable decorate(Runnable runnable) {
                return () -> {
                    userDecoratorApplied.set(true);
                    runnable.run();
                };
            }
        });
        bpp.postProcessBeforeInitialization(executor, "tpte2");
        executor.initialize();
        try {
            MDC.put("traceId", "compose-trace");
            assertThat(executor.submit(() -> MDC.get("traceId")).get()).isEqualTo("compose-trace");
            assertThat(userDecoratorApplied).isTrue();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void wrapsPlainExecutorBean() {
        Executor direct = Runnable::run;
        Object processed = bpp.postProcessAfterInitialization(direct, "directExecutor");

        assertThat(processed).isNotSameAs(direct).isInstanceOf(Executor.class);
    }
}
