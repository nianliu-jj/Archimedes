package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final ArchimedesApiProperties properties;

    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        this.handlerMappings = handlerMappings;
        this.properties = properties;
    }

    /**
     * 提取合并后的路径。跨版本关键点：Boot 3（或 Boot 2 开启 PathPattern）用
     * PathPatternsRequestCondition；Boot 2 默认用 PatternsRequestCondition。两者取其一非空。
     */
    static List<String> extractPaths(RequestMappingInfo mappingInfo) {
        PathPatternsRequestCondition pathPatterns = mappingInfo.getPathPatternsCondition();
        if (pathPatterns != null && !pathPatterns.getPatterns().isEmpty()) {
            return pathPatterns.getPatterns().stream()
                    .map(PathPattern::getPatternString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        PatternsRequestCondition patterns = mappingInfo.getPatternsCondition();
        if (patterns != null && !patterns.getPatterns().isEmpty()) {
            return patterns.getPatterns().stream()
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
