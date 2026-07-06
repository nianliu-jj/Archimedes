package io.github.nianliu.archimedes.trace;

/**
 * 用户自定义 traceId 解析 SPI：从请求中按项目自有规则解析 traceId（优先级最高）。
 * 返回 null/空串表示放弃，落入后续解析链（请求头 → 宿主 MDC → 生成器）。
 *
 * <p>设计要点：置于解析链首位，让宿主项目能够以任意规则（如从自定义头、body、
 * 网关透传字段中提取）覆盖框架的默认行为，实现与既有链路追踪体系的对接。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface TraceIdResolver {

    /**
     * 从请求中解析 traceId。
     *
     * @param request 请求的最小抽象，见 {@link TraceRequest}
     * @return 解析得到的 traceId；返回 null 或空串表示放弃，交由后续解析链处理
     */
    String resolve(TraceRequest request);
}
