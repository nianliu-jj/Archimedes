package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiCatalog;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.RestApiContributor;
import io.github.nianliu.archimedes.scanner.rpc.RpcApiContributor;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Archimedes 内置 API 控制器：挂载在 {@code {base-path}} 下的两个端点——
 * <ul>
 *   <li>{@code GET {base-path}/apis}：聚合 REST/WebSocket/RPC 各协议扫描结果，
 *       返回分组 JSON（{@link ApiCatalog}），前端 UI 与外部调用方的数据入口；</li>
 *   <li>{@code GET {base-path}}：返回内置 UI 页面（将 HTML 模板中的
 *       占位符替换为运行时实际 API 地址后缓存）。</li>
 * </ul>
 * 纯注解式控制器，零 servlet 依赖——Servlet 与 WebFlux 两栈可复用同一实例，
 * 区别仅在注入的 {@link RestApiContributor} 实现不同。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
@RestController
public class ArchimedesApiController {

    /** UI 页面模板在 classpath 中的路径。 */
    private static final String UI_RESOURCE = "archimedes-ui/index.html";
    /** HTML 模板中的 API 地址占位符，渲染时替换为实际 {base-path}/apis。 */
    private static final String API_URL_PLACEHOLDER = "__ARCHIMEDES_API_URL__";

    /** REST 契约扫描器（Servlet 或 Reactive 实现）。 */
    private final RestApiContributor scanner;
    /** 端点配置（base-path、UI 开关等）。 */
    private final ArchimedesApiProperties properties;
    /** WebSocket 契约贡献者列表（宿主没用 WS 时为空列表）。 */
    private final List<WebSocketApiContributor> webSocketContributors;
    /** RPC 契约贡献者列表（宿主没用 RPC 时为空列表）。 */
    private final List<RpcApiContributor> rpcContributors;
    /** UI 页面缓存：首次渲染后不变（路由表启动后固定），避免每次请求重新读 classpath 资源。 */
    private final AtomicReference<String> renderedUi = new AtomicReference<>();

    /** 最简构造：仅 REST 契约，无 WebSocket/RPC 贡献者。 */
    public ArchimedesApiController(RestApiContributor scanner, ArchimedesApiProperties properties) {
        this(scanner, properties, Collections.<WebSocketApiContributor>emptyList(),
                Collections.<RpcApiContributor>emptyList());
    }

    /** 含 WebSocket 但无 RPC 贡献者（兼容旧调用点）。 */
    public ArchimedesApiController(RestApiContributor scanner, ArchimedesApiProperties properties,
                                   List<WebSocketApiContributor> webSocketContributors) {
        this(scanner, properties, webSocketContributors, Collections.<RpcApiContributor>emptyList());
    }

    /** 全量构造：自动装配入口，各贡献者允许为 null（防御性处理为空列表）。 */
    public ArchimedesApiController(RestApiContributor scanner, ArchimedesApiProperties properties,
                                   List<WebSocketApiContributor> webSocketContributors,
                                   List<RpcApiContributor> rpcContributors) {
        this.scanner = scanner;
        this.properties = properties;
        this.webSocketContributors = webSocketContributors == null
                ? Collections.<WebSocketApiContributor>emptyList()
                : webSocketContributors;
        this.rpcContributors = rpcContributors == null
                ? Collections.<RpcApiContributor>emptyList()
                : rpcContributors;
    }

    /** 契约 JSON 端点：聚合全部协议扫描结果为分组 ApiCatalog 返回。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/apis", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiCatalog apis() {
        List<WsApiInfo> webSocketApis = new ArrayList<>();
        for (WebSocketApiContributor contributor : webSocketContributors) {
            webSocketApis.addAll(contributor.contribute());
        }
        List<RpcApiInfo> rpcApis = new ArrayList<>();
        for (RpcApiContributor contributor : rpcContributors) {
            rpcApis.addAll(contributor.contribute());
        }
        return new ApiCatalog(scanner.scan(), webSocketApis, rpcApis);
    }

    /** 内置 UI 端点：ui-enabled=false 时返回 404，否则返回带注入 API 地址的 HTML 页面。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ui() throws IOException {
        if (!properties.isUiEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderUi());
    }

    /** 读取 classpath HTML 模板，替换占位符为实际 API 地址后 CAS 缓存（线程安全，仅首次实际读 IO）。 */
    private String renderUi() throws IOException {
        String cached = renderedUi.get();
        if (cached != null) {
            return cached;
        }
        String template;
        try (InputStream in = new ClassPathResource(UI_RESOURCE).getInputStream()) {
            template = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        String rendered = template.replace(API_URL_PLACEHOLDER, properties.getBasePath() + "/apis");
        renderedUi.compareAndSet(null, rendered);
        return renderedUi.get();
    }
}
