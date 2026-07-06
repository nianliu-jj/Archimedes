package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import io.github.nianliu.archimedes.trace.TraceProperties;
import io.github.nianliu.archimedes.trace.TraceScope;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * javax Servlet 栈的 traceId Filter 薄壳（SB2 侧）：与 boot3 TraceIdFilter 镜像，
 * 仅 servlet API 包名为 javax。trace 编排逻辑委托 core {@link TraceContextManager}。
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

    /** 核心过滤逻辑：非 HTTP 请求直接放行；HTTP 请求建立 trace 上下文并在 finally 中精准恢复 MDC。 */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        final TraceScope scope = manager.begin(httpRequest::getHeader);
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
