package io.github.nianliu.archimedes.scanner;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;

class PathExtractionTest {

    @Test
    void extractsAntPatterns() {
        // 默认 builder = PatternsRequestCondition（Boot 2 默认的 Ant 风格）
        RequestMappingInfo info = RequestMappingInfo.paths("/b", "/a").build();

        assertThat(RestApiScanner.extractPaths(info)).containsExactly("/a", "/b");
    }

    @Test
    void extractsPathPatterns() {
        // 显式 PathPatternParser = PathPatternsRequestCondition（Boot 3 默认）
        RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
        config.setPatternParser(new PathPatternParser());
        RequestMappingInfo info = RequestMappingInfo.paths("/modern/{id}").options(config).build();

        assertThat(RestApiScanner.extractPaths(info)).containsExactly("/modern/{id}");
    }
}
