package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class ArchimedesApiController {

    private static final String UI_RESOURCE = "archimedes-ui/index.html";
    private static final String API_URL_PLACEHOLDER = "__ARCHIMEDES_API_URL__";

    private final RestApiScanner scanner;
    private final ArchimedesApiProperties properties;
    private final AtomicReference<String> renderedUi = new AtomicReference<>();

    public ArchimedesApiController(RestApiScanner scanner, ArchimedesApiProperties properties) {
        this.scanner = scanner;
        this.properties = properties;
    }

    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/apis", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ApiInfo> apis() {
        return scanner.scan();
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
