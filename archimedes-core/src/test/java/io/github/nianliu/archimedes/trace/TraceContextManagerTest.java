package io.github.nianliu.archimedes.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextManagerTest {

    private final TraceProperties props = new TraceProperties();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private TraceContextManager manager(TraceIdGenerator generator, TraceIdResolver resolver) {
        return new TraceContextManager(props, generator, resolver);
    }

    @Test
    void generatesWhenNothingProvided() {
        TraceScope scope = manager(() -> "gen-1", null).begin(name -> null);

        assertThat(scope.getTraceId()).isEqualTo("gen-1");
        assertThat(MDC.get("traceId")).isEqualTo("gen-1");
        assertThat(MDC.get("spanId")).hasSize(16);

        scope.close();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void headerBeatsGenerator() {
        TraceScope scope = manager(() -> "gen-1", null)
                .begin(name -> "X-Trace-Id".equals(name) ? "from-header" : null);

        assertThat(scope.getTraceId()).isEqualTo("from-header");
        assertThat(MDC.get("traceId")).isEqualTo("from-header");
        scope.close();
    }

    @Test
    void resolverBeatsHeader() {
        TraceScope scope = manager(() -> "gen-1", request -> "from-resolver")
                .begin(name -> "from-header");

        assertThat(scope.getTraceId()).isEqualTo("from-resolver");
        scope.close();
    }

    @Test
    void blankResolverResultFallsThrough() {
        TraceScope scope = manager(() -> "gen-1", request -> "  ")
                .begin(name -> null);

        assertThat(scope.getTraceId()).isEqualTo("gen-1");
        scope.close();
    }

    @Test
    void useProjectTraceIdTrustsHostMdc() {
        props.setUseProjectTraceId(true);
        MDC.put("traceId", "host-1");

        TraceScope scope = manager(() -> "gen-1", null).begin(name -> null);

        assertThat(scope.getTraceId()).isEqualTo("host-1");
        scope.close();
        // 宿主的值不写不清
        assertThat(MDC.get("traceId")).isEqualTo("host-1");
    }

    @Test
    void closeRestoresPreviousHostValueAndKeepsUnrelatedKeys() {
        MDC.put("traceId", "old-host-value");
        MDC.put("tenant", "acme");

        TraceScope scope = manager(() -> "gen-1", null).begin(name -> "new-id");
        assertThat(MDC.get("traceId")).isEqualTo("new-id");

        scope.close();
        assertThat(MDC.get("traceId")).isEqualTo("old-host-value");
        assertThat(MDC.get("tenant")).isEqualTo("acme");
    }

    @Test
    void customMdcKeysHonored() {
        props.setMdcKey("tid");
        props.setSpanIdKey("sid");

        TraceScope scope = manager(() -> "gen-1", null).begin(name -> null);

        assertThat(MDC.get("tid")).isEqualTo("gen-1");
        assertThat(MDC.get("sid")).isNotBlank();
        scope.close();
        assertThat(MDC.get("tid")).isNull();
        assertThat(MDC.get("sid")).isNull();
    }

    @Test
    void customHeaderNameHonored() {
        props.setHeaderName("X-Request-Id");

        TraceScope scope = manager(() -> "gen-1", null)
                .begin(name -> "X-Request-Id".equals(name) ? "req-9" : null);

        assertThat(scope.getTraceId()).isEqualTo("req-9");
        scope.close();
    }
}
