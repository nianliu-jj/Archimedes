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
 * jakarta 薄壳：trace 编排逻辑全部在 core 的 TraceContextManager。
 */
public class TraceIdFilter implements Filter {

    private final TraceContextManager manager;
    private final TraceProperties properties;

    public TraceIdFilter(TraceContextManager manager, TraceProperties properties) {
        this.manager = manager;
        this.properties = properties;
    }

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
