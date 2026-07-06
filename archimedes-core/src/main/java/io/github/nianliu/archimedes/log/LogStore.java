package io.github.nianliu.archimedes.log;

/**
 * 日志存储 SPI。默认实现为内存有界存储（InMemoryLogStore）；
 * 宿主可注册自己的 Bean（如 Elasticsearch 实现）替换，starter 侧以 @ConditionalOnMissingBean 让位。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface LogStore {

    /** 追加一条日志（处于日志热路径，实现应尽量轻量、避免阻塞）。 */
    void append(LogEntry entry);

    /**
     * 按 traceId 查询，时间升序分页。
     *
     * @param page 1 起始页码
     */
    LogQueryResult queryByTraceId(String traceId, int page, int size);
}
