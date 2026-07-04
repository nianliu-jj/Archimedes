package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchimedesApiControllerTest {

    private final RestApiScanner scanner = mock(RestApiScanner.class);

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
