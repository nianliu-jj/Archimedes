package io.github.nianliu.archimedes.trace;

/**
 * traceId 生成器 SPI。默认实现为 UUID；宿主可注册自己的 Bean（如雪花算法）替换，
 * starter 侧以 @ConditionalOnMissingBean 让位。
 *
 * <p>设计要点：作为整条 traceId 解析链的兜底环节，当解析器、请求头、宿主 MDC 均未提供
 * traceId 时，由该生成器产出一个全新的 traceId，确保每条请求链路都拥有唯一标识。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface TraceIdGenerator {

    /**
     * 生成一个全新的 traceId。
     *
     * @return 非空、全局唯一的 traceId 字符串
     */
    String generate();
}
