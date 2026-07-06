package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import io.github.nianliu.archimedes.trace.TraceProperties;
import io.github.nianliu.archimedes.trace.TraceScope;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * jakarta Servlet 栈的 traceId Filter 薄壳：将 HTTP 请求/响应的 trace 编排
 * 委托给 core 层的 {@link TraceContextManager}（零 servlet 依赖的纯逻辑），
 * 本类仅负责 jakarta.servlet 类型适配。
 * <p>注册优先级 {@code Ordered.HIGHEST_PRECEDENCE}，确保在业务 Filter 之前建立 trace 上下文；
 * 仅拦截 REQUEST dispatch（FORWARD/INCLUDE/ERROR 等不重复建立）。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class TraceIdFilter implements Filter {

    private final TraceContextManager manager;
    private final TraceProperties properties;

    public TraceIdFilter(TraceContextManager manager, TraceProperties properties) {
        this.manager = manager;
        this.properties = properties;
    }

    /**
     * 核心过滤逻辑：非 HTTP 请求直接放行；HTTP 请求经 TraceContextManager 建立
     * trace 上下文（解析/生成 traceId + 写 MDC），可选地在响应头回写 traceId，
     * 请求结束后 TraceScope.close() 精准恢复 MDC 旧值。
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        TraceScope scope = manager.begin(httpRequest::getHeader);
        try {
            if (properties.isResponseHeader() && response instanceof HttpServletResponse) {
                ((HttpServletResponse) response).setHeader(properties.getHeaderName(), scope.getTraceId());
            }
            chain.doFilter(request, response);
        } finally {
            scope.close();
        }
    }
}
