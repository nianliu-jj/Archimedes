package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reactive（WebFlux）栈 REST 扫描器：读取 spring-webflux 的
 * RequestMappingHandlerMapping。响应式栈始终使用 PathPattern 路由，
 * 无 AntPathMatcher 回退分支；编译面 API 在 Spring 5.3 与 6.x 一致，
 * 单 jar 双端复用。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class ReactiveRestApiScanner extends AbstractRestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRestApiScanner.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;

    /** 注入所有 WebFlux 栈的 RequestMappingHandlerMapping。 */
    public ReactiveRestApiScanner(List<RequestMappingHandlerMapping> handlerMappings,
                                  ArchimedesApiProperties properties) {
        super(properties);
        this.handlerMappings = handlerMappings;
    }

    /** 遍历响应式 handler 表，逐条转 ApiInfo；单条解析失败仅告警跳过，不中断整体扫描。 */
    @Override
    protected void collectApis(List<ApiInfo> sink) {
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                try {
                    addIfIncluded(sink, toApiInfo(entry.getKey(), entry.getValue()));
                } catch (Exception ex) {
                    // 单个响应式 handler 结构异常不应拖垮整份契约，降级为告警
                    log.warn("Archimedes: failed to parse reactive handler {}, skipped", entry.getValue(), ex);
                }
            }
        }
    }

    /**
     * 从 WebFlux 的 RequestMappingInfo 抽取条件交由父类模板组装。
     * 响应式栈路由固定使用 PathPattern，故直接读 PatternsCondition，无 Servlet 栈的 AntPathMatcher 回退。
     */
    ApiInfo toApiInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        return buildApiInfo(handlerMethod,
                mappingInfo.getPatternsCondition().getPatterns().stream()
                        .map(PathPattern::getPatternString).sorted().collect(Collectors.toList()),
                mappingInfo.getMethodsCondition().getMethods().stream()
                        .map(Enum::name).sorted().collect(Collectors.toList()),
                mappingInfo.getConsumesCondition().getConsumableMediaTypes().stream()
                        .map(Object::toString).sorted().collect(Collectors.toList()),
                mappingInfo.getProducesCondition().getProducibleMediaTypes().stream()
                        .map(Object::toString).sorted().collect(Collectors.toList()));
    }
}
