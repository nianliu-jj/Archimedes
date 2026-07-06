package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.FieldInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST 扫描骨架：缓存模板、参数/返回类型提取、排除规则与排序均与 Web 栈无关，
 * 下沉于此；Servlet 与 Reactive 子类只负责从各自栈的 HandlerMapping 取
 * 路径/方法/媒体类型条件（{@link HandlerMethod} 为两栈共享类型）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public abstract class AbstractRestApiScanner implements RestApiContributor {

    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final ArchimedesApiProperties properties;
    private final AtomicReference<List<ApiInfo>> cache = new AtomicReference<>();

    protected AbstractRestApiScanner(ArchimedesApiProperties properties) {
        this.properties = properties;
    }

    /** 首次调用扫描并缓存；后续返回同一不可变列表。 */
    @Override
    public final List<ApiInfo> scan() {
        List<ApiInfo> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        cache.compareAndSet(null, doScan());
        return cache.get();
    }

    private List<ApiInfo> doScan() {
        List<ApiInfo> apis = new ArrayList<>();
        collectApis(apis);
        apis.sort(Comparator
                .comparing((ApiInfo a) -> a.getPaths().isEmpty() ? "" : a.getPaths().get(0))
                .thenComparing(a -> a.getHttpMethods().isEmpty() ? "" : a.getHttpMethods().get(0)));
        return Collections.unmodifiableList(apis);
    }

    /** 子类遍历各自栈的 HandlerMapping，构建 ApiInfo 后经 {@link #addIfIncluded} 汇入。 */
    protected abstract void collectApis(List<ApiInfo> sink);

    /** 应用排除规则（自身端点、/error、base-packages 过滤）后加入结果集。 */
    protected final void addIfIncluded(List<ApiInfo> sink, ApiInfo info) {
        if (!isExcluded(info)) {
            sink.add(info);
        }
    }

    /** 由栈特定的映射条件 + 共享的 HandlerMethod 反射信息组装 ApiInfo。 */
    protected final ApiInfo buildApiInfo(HandlerMethod handlerMethod, List<String> paths,
                                         List<String> httpMethods, List<String> consumes,
                                         List<String> produces) {
        Method method = handlerMethod.getMethod();
        ApiInfo info = new ApiInfo();
        info.setControllerClass(handlerMethod.getBeanType().getName());
        info.setHandlerMethod(method.getName());
        info.setPaths(paths);
        info.setHttpMethods(httpMethods);
        info.setParams(extractParams(handlerMethod));
        info.setReturnType(method.getGenericReturnType().getTypeName());
        info.setConsumes(consumes);
        info.setProduces(produces);
        info.setDeprecated(method.isAnnotationPresent(Deprecated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class));
        // 契约增强：请求体/响应体字段结构（解析失败降级 null，不影响主体）
        info.setRequestBodySchema(resolveRequestBodySchema(handlerMethod));
        info.setResponseSchema(TypeSchemaResolver.resolve(method.getGenericReturnType()));
        return info;
    }

    /** 取首个 @RequestBody 参数的泛型类型做结构解析；无 BODY 参数返回 null。 */
    private FieldInfo resolveRequestBodySchema(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.getParameterAnnotation(RequestBody.class) != null) {
                return TypeSchemaResolver.resolve(parameter.getGenericParameterType());
            }
        }
        return null;
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
        // 参数说明：Swagger @Parameter/@ApiParam 反射读取（宿主没用 Swagger 时为空串）
        String description = TypeSchemaResolver.paramDescription(parameter.getParameterAnnotations());

        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = firstNonEmpty(requestParam.name(), requestParam.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.QUERY, type, requestParam.required(), description);
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = firstNonEmpty(pathVariable.name(), pathVariable.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.PATH, type, pathVariable.required(), description);
        }
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String name = firstNonEmpty(requestHeader.name(), requestHeader.value(), fallbackName(parameter));
            return new ParamInfo(name, ParamSource.HEADER, type, requestHeader.required(), description);
        }
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (requestBody != null) {
            return new ParamInfo(fallbackName(parameter), ParamSource.BODY, type, requestBody.required(), description);
        }
        return new ParamInfo(fallbackName(parameter), ParamSource.OTHER, type, false, description);
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
