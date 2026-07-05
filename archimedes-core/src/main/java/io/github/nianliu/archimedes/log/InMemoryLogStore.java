package io.github.nianliu.archimedes.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存有界日志存储：按 traceId 索引（插入序），双上限——
 * 单 trace 超 maxEntriesPerTrace 丢最老条目；全局超 maxEntries 淘汰最老的整条链路
 * （语义：最近的链路完整可查；实现 O(1)，append 处于日志热路径）。
 * 重启即失，生产持久化请通过 LogStore SPI 接入外部存储。
 */
public class InMemoryLogStore implements LogStore {

    private final int maxEntries;
    private final int maxEntriesPerTrace;

    private final Object lock = new Object();
    private final LinkedHashMap<String, Deque<LogEntry>> byTrace = new LinkedHashMap<>();
    private int totalCount = 0;

    public InMemoryLogStore(int maxEntries, int maxEntriesPerTrace) {
        this.maxEntries = maxEntries;
        this.maxEntriesPerTrace = maxEntriesPerTrace;
    }

    @Override
    public void append(LogEntry entry) {
        if (entry == null || entry.getTraceId() == null) {
            return;
        }
        synchronized (lock) {
            Deque<LogEntry> traceLogs = byTrace.get(entry.getTraceId());
            if (traceLogs == null) {
                traceLogs = new ArrayDeque<>();
                byTrace.put(entry.getTraceId(), traceLogs);
            }
            if (traceLogs.size() >= maxEntriesPerTrace) {
                traceLogs.pollFirst();
                totalCount--;
            }
            traceLogs.addLast(entry);
            totalCount++;

            while (totalCount > maxEntries && byTrace.size() > 1) {
                Iterator<Map.Entry<String, Deque<LogEntry>>> it = byTrace.entrySet().iterator();
                Map.Entry<String, Deque<LogEntry>> oldest = it.next();
                if (oldest.getKey().equals(entry.getTraceId())) {
                    break; // 只剩当前链路时不自噬
                }
                totalCount -= oldest.getValue().size();
                it.remove();
            }
        }
    }

    @Override
    public LogQueryResult queryByTraceId(String traceId, int page, int size) {
        List<LogEntry> snapshot;
        synchronized (lock) {
            Deque<LogEntry> traceLogs = byTrace.get(traceId);
            snapshot = traceLogs == null ? new ArrayList<LogEntry>() : new ArrayList<>(traceLogs);
        }
        snapshot.sort(Comparator.comparingLong(LogEntry::getTimestampMillis));

        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int from = Math.min((safePage - 1) * safeSize, snapshot.size());
        int to = Math.min(from + safeSize, snapshot.size());
        return new LogQueryResult(traceId, snapshot.size(), safePage, safeSize,
                new ArrayList<>(snapshot.subList(from, to)));
    }
}
