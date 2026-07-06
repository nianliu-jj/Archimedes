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
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class RestApiScanner extends AbstractRestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;

    /** 注入所有 Servlet 栈的 RequestMappingHandlerMapping（可能存在多个，如 MVC 主映射与自定义映射）。 */
    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        super(properties);
        this.handlerMappings = handlerMappings;
    }

    /** 遍历每个映射的 handler 表，逐条转 ApiInfo；单条解析失败仅告警跳过，不中断整体扫描。 */
    @Override
    protected void collectApis(List<ApiInfo> sink) {
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                try {
                    addIfIncluded(sink, toApiInfo(entry.getKey(), entry.getValue()));
                } catch (Exception ex) {
                    // 单个 handler 结构异常（如泛型怪异）不应拖垮整份契约，降级为告警
                    log.warn("Archimedes: failed to parse handler {}, skipped", entry.getValue(), ex);
                }
            }
        }
    }

    /** 从 Servlet 栈的 RequestMappingInfo 抽取路径/HTTP 方法/媒体类型条件，交由父类模板组装。 */
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

    /**
     * 提取映射路径，兼容两种路由条件：
     * 优先 PathPatternsCondition（Spring 5.3+ 默认的 PathPattern 解析器），
     * 回退 PatternsCondition（显式配置 AntPathMatcher 时使用）；两者皆空返回空列表。
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
