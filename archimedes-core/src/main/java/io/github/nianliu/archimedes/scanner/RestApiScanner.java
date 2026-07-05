package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servlet 栈 REST 扫描器：读取 spring-webmvc 的 RequestMappingHandlerMapping，
 * 路径提取兼容 PathPattern（5.3+ 默认）与 AntPathMatcher（旧配置）两种条件。
 */
public class RestApiScanner extends AbstractRestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;

    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        super(properties);
        this.handlerMappings = handlerMappings;
    }

    @Override
    protected void collectApis(List<ApiInfo> sink) {
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                try {
                    addIfIncluded(sink, toApiInfo(entry.getKey(), entry.getValue()));
                } catch (Exception ex) {
                    log.warn("Archimedes: failed to parse handler {}, skipped", entry.getValue(), ex);
                }
            }
        }
    }

    ApiInfo toApiInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        return buildApiInfo(handlerMethod,
                extractPaths(mappingInfo),
                mappingInfo.getMethodsCondition().getMethods().stream()
                        .map(Enum::name).sorted().collect(Collectors.toList()),
                mappingInfo.getConsumesCondition().getConsumableMediaTypes().stream()
                        .map(Object::toString).sorted().collect(Collectors.toList()),
                mappingInfo.getProducesCondition().getProducibleMediaTypes().stream()
                        .map(Object::toString).sorted().collect(Collectors.toList()));
    }

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
