package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiCatalog;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
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

@RestController
public class ArchimedesApiController {

    private static final String UI_RESOURCE = "archimedes-ui/index.html";
    private static final String API_URL_PLACEHOLDER = "__ARCHIMEDES_API_URL__";

    private final RestApiScanner scanner;
    private final ArchimedesApiProperties properties;
    private final List<WebSocketApiContributor> webSocketContributors;
    private final List<RpcApiContributor> rpcContributors;
    private final AtomicReference<String> renderedUi = new AtomicReference<>();

    public ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties) {
        this(scanner, properties, Collections.<WebSocketApiContributor>emptyList(),
                Collections.<RpcApiContributor>emptyList());
    }

    public ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties,
                                   List<WebSocketApiContributor> webSocketContributors) {
        this(scanner, properties, webSocketContributors, Collections.<RpcApiContributor>emptyList());
    }

    public ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties,
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

    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ui() throws IOException {
        if (!properties.isUiEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderUi());
    }

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
