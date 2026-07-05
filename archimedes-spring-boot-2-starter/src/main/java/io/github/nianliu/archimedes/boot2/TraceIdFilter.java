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
 * javax 薄壳：trace 编排逻辑全部在 core 的 TraceContextManager。
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
