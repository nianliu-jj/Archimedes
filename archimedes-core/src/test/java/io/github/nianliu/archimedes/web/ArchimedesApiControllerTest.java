package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiCatalog;
import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchimedesApiControllerTest {

    private final RestApiScanner scanner = mock(RestApiScanner.class);

    @Test
    void apisReturnsGroupedCatalogWithEmptyDefaults() {
        ArchimedesApiController controller =
                new ArchimedesApiController(scanner, new ArchimedesApiProperties());

        ApiCatalog catalog = controller.apis();

        assertThat(catalog.getRestApis()).isNotNull();
        assertThat(catalog.getWebSocketApis()).isEmpty();
    }

    @Test
    void apisAggregatesAllWebSocketContributors() {
        WsApiInfo a = new WsApiInfo(WsApiInfo.KIND_HANDLER, "/ws/a", "A", null, false);
        WsApiInfo b = new WsApiInfo(WsApiInfo.KIND_SERVER_ENDPOINT, "/ws/b", "B", null, false);
        ArchimedesApiController controller = new ArchimedesApiController(scanner,
                new ArchimedesApiProperties(), List.of(() -> List.of(a), () -> List.of(b)));

        ApiCatalog catalog = controller.apis();

        assertThat(catalog.getWebSocketApis()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void uiInjectsAbsoluteApiUrl() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        ResponseEntity<String> resp = controller.ui();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType().includes(MediaType.TEXT_HTML)).isTrue();
        assertThat(resp.getBody()).contains("/archimedes/apis");
        assertThat(resp.getBody()).doesNotContain("__ARCHIMEDES_API_URL__");
    }

    @Test
    void uiUsesConfiguredBasePath() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePath("/custom");
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        assertThat(controller.ui().getBody()).contains("/custom/apis");
    }

    @Test
    void uiReturns404WhenDisabled() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setUiEnabled(false);
        ArchimedesApiController controller = new ArchimedesApiController(scanner, props);

        assertThat(controller.ui().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
