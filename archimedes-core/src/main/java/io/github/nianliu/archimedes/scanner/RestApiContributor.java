package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.model.ApiInfo;

import java.util.List;

/**
 * REST 契约扫描器 SPI：Servlet 与 Reactive 两栈扫描器的公共出口，
 * {@code ArchimedesApiController} 依赖此抽象从而可复用于任一 Web 栈。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public interface RestApiContributor {

    /** 返回宿主应用的 REST 契约列表（实现应缓存，多次调用返回同一结果）。 */
    List<ApiInfo> scan();
}
