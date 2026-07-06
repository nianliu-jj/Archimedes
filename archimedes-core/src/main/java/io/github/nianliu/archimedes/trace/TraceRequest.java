package io.github.nianliu.archimedes.trace;

/**
 * 请求的最小抽象：core 不依赖 servlet API，javax/jakarta 的 HttpServletRequest
 * 在 starter 侧以方法引用适配（request::getHeader）。
 *
 * <p>设计要点：core 模块保持对 Web 容器零依赖，只声明「按名取请求头」这一最小能力，
 * 从而同时兼容 Servlet（javax/jakarta）与响应式（WebFlux）等不同技术栈。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface TraceRequest {

    /**
     * 按名称获取请求头的值。
     *
     * @param name 请求头名称
     * @return 请求头的值；不存在时返回 null
     */
    String getHeader(String name);
}
