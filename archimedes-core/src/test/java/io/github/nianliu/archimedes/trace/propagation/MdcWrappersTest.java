package io.github.nianliu.archimedes.trace.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class MdcWrappersTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void runnableCarriesSnapshotAndRestoresExecutingThreadMdc() {
        MDC.put("traceId", "from-submitter");
        Runnable wrapped = MdcWrappers.wrap((Runnable) () ->
                assertThat(MDC.get("traceId")).isEqualTo("from-submitter"));

        // 模拟池化线程自己的上下文
        MDC.put("traceId", "worker-own-value");
        wrapped.run();

        assertThat(MDC.get("traceId")).isEqualTo("worker-own-value");
    }

    @Test
    void emptySnapshotClearsChildContextDuringRun() {
        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("sentinel");
        Runnable wrapped = MdcWrappers.wrap((Runnable) () -> seen.set(MDC.get("traceId")));

        MDC.put("traceId", "worker-own-value");
        wrapped.run();

        assertThat(seen.get()).isNull();
        assertThat(MDC.get("traceId")).isEqualTo("worker-own-value");
    }

    @Test
    void callableAndSupplierCarrySnapshot() throws Exception {
        MDC.put("traceId", "t1");
        Callable<String> callable = MdcWrappers.wrap((Callable<String>) () -> MDC.get("traceId"));
        Supplier<String> supplier = MdcWrappers.wrap((Supplier<String>) () -> MDC.get("traceId"));

        MDC.put("traceId", "changed");
        assertThat(callable.call()).isEqualTo("t1");
        assertThat(supplier.get()).isEqualTo("t1");
        assertThat(MDC.get("traceId")).isEqualTo("changed");
    }

    @Test
    void executorServiceWrapsAllSubmissionPaths() throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        try {
            MDC.put("traceId", "pool-trace");
            ExecutorService wrapped = MdcWrappers.wrap(raw);

            assertThat(wrapped.submit(() -> MDC.get("traceId")).get()).isEqualTo("pool-trace");

            AtomicReference<String> fromRunnable = new AtomicReference<>();
            wrapped.submit(() -> fromRunnable.set(MDC.get("traceId"))).get();
            assertThat(fromRunnable.get()).isEqualTo("pool-trace");

            assertThat(wrapped.invokeAll(Arrays.asList(
                    (Callable<String>) () -> MDC.get("traceId"),
                    (Callable<String>) () -> MDC.get("traceId")))
                    .get(0).get()).isEqualTo("pool-trace");

            assertThat(wrapped.invokeAny(Arrays.asList(
                    (Callable<String>) () -> MDC.get("traceId")))).isEqualTo("pool-trace");
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    void wrapIsIdempotent() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        try {
            ExecutorService once = MdcWrappers.wrap(raw);
            assertThat(MdcWrappers.wrap(once)).isSameAs(once);
        } finally {
            raw.shutdownNow();
        }
    }
}
