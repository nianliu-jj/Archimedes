package io.github.nianliu.archimedes.trace;

/**
 * traceId 生成器 SPI。默认实现为 UUID；宿主可注册自己的 Bean（如雪花算法）替换，
 * starter 侧以 @ConditionalOnMissingBean 让位。
 */
public interface TraceIdGenerator {

    String generate();
}
