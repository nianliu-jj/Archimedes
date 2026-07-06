package io.github.nianliu.archimedes.trace;

import java.util.UUID;

/**
 * {@link TraceIdGenerator} 的默认实现，基于 JDK 的 {@link UUID} 生成 traceId。
 *
 * <p>设计要点：去掉 UUID 中的连字符，得到 32 位紧凑十六进制字符串，
 * 便于在日志、请求头中传递，同时保持足够低的碰撞概率。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class UuidTraceIdGenerator implements TraceIdGenerator {

    /**
     * 生成一个基于随机 UUID 的 traceId。
     *
     * @return 去掉连字符的 32 位十六进制字符串
     */
    @Override
    public String generate() {
        // 去掉连字符，得到紧凑的 32 位十六进制串，避免在头部/日志中出现多余分隔符
        return UUID.randomUUID().toString().replace("-", "");
    }
}
