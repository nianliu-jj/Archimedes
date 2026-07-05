package io.github.nianliu.archimedes.trace;

/**
 * 请求的最小抽象：core 不依赖 servlet API，javax/jakarta 的 HttpServletRequest
 * 在 starter 侧以方法引用适配（request::getHeader）。
 */
public interface TraceRequest {

    String getHeader(String name);
}
