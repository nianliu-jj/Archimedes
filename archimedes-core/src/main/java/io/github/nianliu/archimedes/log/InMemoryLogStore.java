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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class InMemoryLogStore implements LogStore {

    /** 全局条目数上限：超过后按链路整条淘汰最老的 trace。 */
    private final int maxEntries;
    /** 单条链路的条目数上限：超过后丢弃该链路最老的一条。 */
    private final int maxEntriesPerTrace;

    /** 保护 byTrace 与 totalCount 的互斥锁（append 与查询快照均在此临界区内）。 */
    private final Object lock = new Object();
    /** traceId -> 该链路日志队列；LinkedHashMap 保持插入序，迭代首个即为最老链路。 */
    private final LinkedHashMap<String, Deque<LogEntry>> byTrace = new LinkedHashMap<>();
    /** 当前全局已存条目总数（随增删同步维护，避免遍历统计）。 */
    private int totalCount = 0;

    public InMemoryLogStore(int maxEntries, int maxEntriesPerTrace) {
        this.maxEntries = maxEntries;
        this.maxEntriesPerTrace = maxEntriesPerTrace;
    }

    /**
     * 追加一条日志到对应链路队列，并执行双上限淘汰。
     * traceId 为空的条目直接丢弃（无法归档聚合）。
     */
    @Override
    public void append(LogEntry entry) {
        if (entry == null || entry.getTraceId() == null) {
            return;
        }
        synchronized (lock) {
            // 取或建该 traceId 的日志队列（首次出现时新建并按插入序登记）
            Deque<LogEntry> traceLogs = byTrace.get(entry.getTraceId());
            if (traceLogs == null) {
                traceLogs = new ArrayDeque<>();
                byTrace.put(entry.getTraceId(), traceLogs);
            }
            // 单链路上限：先淘汰本链路最老一条，保证新日志能入队
            if (traceLogs.size() >= maxEntriesPerTrace) {
                traceLogs.pollFirst();
                totalCount--;
            }
            traceLogs.addLast(entry);
            totalCount++;

            // 全局上限：淘汰整条最老链路（插入序最靠前），直至总量回落
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

    /**
     * 按 traceId 取出该链路全部日志，时间升序后做内存分页。
     * 先在锁内拷贝快照再排序分页，尽量缩短临界区、不阻塞 append 热路径。
     */
    @Override
    public LogQueryResult queryByTraceId(String traceId, int page, int size) {
        List<LogEntry> snapshot;
        synchronized (lock) {
            // 锁内仅做浅拷贝，排序/分页移出临界区
            Deque<LogEntry> traceLogs = byTrace.get(traceId);
            snapshot = traceLogs == null ? new ArrayList<LogEntry>() : new ArrayList<>(traceLogs);
        }
        snapshot.sort(Comparator.comparingLong(LogEntry::getTimestampMillis));

        // 入参兜底：页码/页大小至少为 1，越界时收敛到有效区间，避免下标异常
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int from = Math.min((safePage - 1) * safeSize, snapshot.size());
        int to = Math.min(from + safeSize, snapshot.size());
        return new LogQueryResult(traceId, snapshot.size(), safePage, safeSize,
                new ArrayList<>(snapshot.subList(from, to)));
    }
}
