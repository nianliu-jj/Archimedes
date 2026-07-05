package io.github.nianliu.archimedes.log;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLogStoreTest {

    private LogEntry entry(String traceId, long ts, String message) {
        return new LogEntry(ts, traceId, "span", "INFO", "t", "logger", message);
    }

    @Test
    void queryReturnsTimeSortedEntries() {
        InMemoryLogStore store = new InMemoryLogStore(100, 50);
        store.append(entry("t1", 300, "third"));
        store.append(entry("t1", 100, "first"));
        store.append(entry("t1", 200, "second"));

        List<String> messages = store.queryByTraceId("t1", 1, 10).getLogs().stream()
                .map(LogEntry::getMessage).collect(Collectors.toList());

        assertThat(messages).containsExactly("first", "second", "third");
    }

    @Test
    void paginationSlicesAndReportsTotal() {
        InMemoryLogStore store = new InMemoryLogStore(100, 50);
        for (int i = 1; i <= 5; i++) {
            store.append(entry("t1", i, "m" + i));
        }

        LogQueryResult page1 = store.queryByTraceId("t1", 1, 3);
        assertThat(page1.getTotal()).isEqualTo(5);
        assertThat(page1.getLogs()).hasSize(3);
        assertThat(page1.getLogs().get(0).getMessage()).isEqualTo("m1");

        LogQueryResult page2 = store.queryByTraceId("t1", 2, 3);
        assertThat(page2.getLogs()).hasSize(2);
        assertThat(page2.getLogs().get(1).getMessage()).isEqualTo("m5");
    }

    @Test
    void perTraceCapDropsOldestEntry() {
        InMemoryLogStore store = new InMemoryLogStore(100, 3);
        for (int i = 1; i <= 5; i++) {
            store.append(entry("t1", i, "m" + i));
        }

        LogQueryResult result = store.queryByTraceId("t1", 1, 10);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getLogs().get(0).getMessage()).isEqualTo("m3");
    }

    @Test
    void globalCapEvictsOldestTraceEntirely() {
        InMemoryLogStore store = new InMemoryLogStore(4, 10);
        store.append(entry("old", 1, "o1"));
        store.append(entry("old", 2, "o2"));
        store.append(entry("new", 3, "n1"));
        store.append(entry("new", 4, "n2"));
        store.append(entry("new", 5, "n3")); // 超全局上限，old 整体淘汰

        assertThat(store.queryByTraceId("old", 1, 10).getTotal()).isZero();
        assertThat(store.queryByTraceId("new", 1, 10).getTotal()).isEqualTo(3);
    }

    @Test
    void singleTraceNeverSelfEvictsViaGlobalCap() {
        InMemoryLogStore store = new InMemoryLogStore(2, 10);
        for (int i = 1; i <= 5; i++) {
            store.append(entry("only", i, "m" + i));
        }
        // 只剩当前链路时不自噬（受 per-trace 上限约束即可）
        assertThat(store.queryByTraceId("only", 1, 10).getTotal()).isEqualTo(5);
    }

    @Test
    void unknownTraceReturnsEmptyResult() {
        InMemoryLogStore store = new InMemoryLogStore(10, 10);
        LogQueryResult result = store.queryByTraceId("nope", 1, 10);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getLogs()).isEmpty();
    }
}
