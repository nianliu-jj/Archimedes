package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiScannerExclusionTest {

    @RestController
    static class MixedController {
        @GetMapping("/error/custom")
        public String err() {
            return "";
        }

        @GetMapping("/archimedes/thing")
        public String underBasePath() {
            return "";
        }

        @GetMapping("/keep/me")
        public String keep() {
            return "";
        }
    }

    private List<String> scannedPaths(ArchimedesApiProperties props, Class<?> controller) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controller);
        return new RestApiScanner(List.of(mapping), props).scan().stream()
                .flatMap(a -> a.getPaths().stream())
                .toList();
    }

    @Test
    void excludesErrorAndOwnEndpoints() {
        List<String> paths = scannedPaths(new ArchimedesApiProperties(), MixedController.class);
        assertThat(paths).containsExactly("/keep/me");
    }

    @Test
    void basePackagesFilterExcludesNonMatching() {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePackages(List.of("com.nonexistent"));

        List<String> paths = scannedPaths(props, SampleControllers.UserController.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void basePackagesFilterKeepsMatching() {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.setBasePackages(List.of("io.github.nianliu"));

        List<ApiInfo> apis = new RestApiScanner(
                List.of(SampleControllers.buildMapping(SampleControllers.UserController.class)), props).scan();
        assertThat(apis).isNotEmpty();
    }
}
