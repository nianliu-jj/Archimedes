package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class RestApiScanner {

    private static final Logger log = LoggerFactory.getLogger(RestApiScanner.class);
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final ArchimedesApiProperties properties;
    private final AtomicReference<List<ApiInfo>> cache = new AtomicReference<>();

    public RestApiScanner(List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        this.handlerMappings = handlerMappings;
        this.properties = properties;
    }

    /** 首次调用扫描并缓存；后续返回同一不可变列表。 */
    public List<ApiInfo> scan() {
        List<ApiInfo> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        cache.compareAndSet(null, doScan());
        return cache.get();
    }

    private List<ApiInfo> doScan() {
        List<ApiInfo> apis = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                try {
                    ApiInfo info = toApiInfo(entry.getKey(), entry.getValue());
                    if (!isExcluded(info)) {
                        apis.add(info);
                    }
                } catch (Exception ex) {
                    log.warn("Archimedes: failed to parse handler {}, skipped", entry.getValue(), ex);
                }
            }
        }
        apis.sort(Comparator
                .comparing((ApiInfo a) -> a.getPaths().isEmpty() ? "" : a.getPaths().get(0))
                .thenComparing(a -> a.getHttpMethods().isEmpty() ? "" : a.getHttpMethods().get(0)));
        return Collections.unmodifiableList(apis);
    }

    ApiInfo toApiInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        ApiInfo info = new ApiInfo();
        info.setControllerClass(handlerMethod.getBeanType().getName());
        info.setHandlerMethod(method.getName());
        info.setPaths(extractPaths(mappingInfo));
        info.setHttpMethods(mappingInfo.getMethodsCondition().getMethods().stream()
                .map(Enum::name).sorted().collect(Collectors.toList()));
        info.setParams(extractParams(handlerMethod));
        info.setReturnType(method.getGenericReturnType().getTypeName());
        info.setConsumes(mappingInfo.getConsumesCondition().getConsumableMediaTypes().stream()
                .map(Object::toString).sorted().collect(Collectors.toList()));
        info.setProduces(mappingInfo.getProducesCondition().getProducibleMediaTypes().stream()
                .map(Object::toString).sorted().collect(Collectors.toList()));
        info.setDeprecated(method.isAnnotationPresent(Deprecated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class));
        return info;
    }

    private boolean isExcluded(ApiInfo info) {
        String controllerClass = info.getControllerClass();
        if (controllerClass.contains("BasicErrorController")) {
            return true;
        }
        String basePath = properties.getBasePath();
        boolean underBasePath = info.getPaths().stream()
                .anyMatch(p -> p.equals(basePath) || p.startsWith(basePath + "/"));
        if (underBasePath) {
            return true;
        }
        boolean isError = info.getPaths().stream()
                .anyMatch(p -> p.equals("/error") || p.startsWith("/error/"));
        if (isError) {
            return true;
        }
        List<String> basePackages = properties.getBasePackages();
        if (basePackages != null && !basePackages.isEmpty()) {
            return basePackages.stream().noneMatch(controllerClass::startsWith);
        }
        return false;
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

    private List<ParamInfo> extractParams(HandlerMethod handlerMethod) {
        List<ParamInfo> params = new ArrayList<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            parameter.initParameterNameDiscovery(PARAM_NAME_DISCOVERER);
            params.add(toParamInfo(parameter));
        }
        return params;
    }

    private ParamInfo toParamInfo(MethodParameter parameter) {
        String type = parameter.getGenericParameterType().getTypeName();

        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = firstNonEmpty(requestParam.name(), requestParam.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.QUERY, type, requestParam.required());
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = firstNonEmpty(pathVariable.name(), pathVariable.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.PATH, type, pathVariable.required());
        }
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String name = firstNonEmpty(requestHeader.name(), requestHeader.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.HEADER, type, requestHeader.required());
        }
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (requestBody != null) {
            return new ParamInfo(fallbackName(parameter), ParamSource.BODY, type, requestBody.required());
        }
        return new ParamInfo(fallbackName(parameter), ParamSource.OTHER, type, false);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String fallbackName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
