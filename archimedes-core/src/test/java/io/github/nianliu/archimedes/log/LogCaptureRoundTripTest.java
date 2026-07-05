package io.github.nianliu.archimedes.log;

import io.github.nianliu.archimedes.trace.TraceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真 logback 环境往返：Initializer 挂载 → 打日志 → store 收到结构化条目 → 卸载后不再采集。
 */
class LogCaptureRoundTripTest {

    private final InMemoryLogStore store = new InMemoryLogStore(100, 50);
    private final LogCaptureInitializer initializer =
            new LogCaptureInitializer(store, new TraceProperties());

    @AfterEach
    void tearDown() {
        initializer.destroy();
        MDC.clear();
    }

    @Test
    void capturesOnlyEventsWithTraceIdAndDetachesOnDestroy() {
        initializer.afterPropertiesSet();
        Logger logger = LoggerFactory.getLogger("capture.roundtrip");

        logger.info("no trace id yet"); // 无 traceId 不采集

        MDC.put("traceId", "rt-1");
        MDC.put("spanId", "span-9");
        logger.warn("hello {}", "capture");

        LogQueryResult result = store.queryByTraceId("rt-1", 1, 10);
        assertThat(result.getTotal()).isEqualTo(1);
        LogEntry entry = result.getLogs().get(0);
        assertThat(entry.getMessage()).isEqualTo("hello capture");
        assertThat(entry.getLevel()).isEqualTo("WARN");
        assertThat(entry.getLogger()).isEqualTo("capture.roundtrip");
        assertThat(entry.getSpanId()).isEqualTo("span-9");
        assertThat(entry.getThread()).isNotBlank();
        assertThat(entry.getTimestamp()).isNotBlank();

        initializer.destroy();
        logger.warn("after detach");
        assertThat(store.queryByTraceId("rt-1", 1, 10).getTotal()).isEqualTo(1);
    }
}
