package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.annotation.ApiParam;
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

    /** 参数名探测器：优先读 -parameters 编译信息，回退字节码 LocalVariableTable，供无注解 name 时兜底。 */
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final ArchimedesApiProperties properties;

    /** 扫描结果缓存：契约在应用启动后不变，用 CAS 保证并发首扫只落一份不可变列表。 */
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
        // CAS：并发首扫时可能多个线程各算一份，但只有一份被写入，其余作废；保证外部始终看到同一实例
        cache.compareAndSet(null, doScan());
        return cache.get();
    }

    /** 清除缓存，下次 scan() 时重新扫描（热监听功能使用：检测到路由表变化时刷新契约）。 */
    public void invalidateCache() {
        cache.set(null);
    }

    /** 收集全部 ApiInfo 后按"首路径 → 首 HTTP 方法"稳定排序，输出为不可变列表方便安全共享。 */
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
                || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class)
                || TypeSchemaResolver.operationDeprecated(method.getAnnotations()));
        // 契约增强：请求体/响应体字段结构（解析失败降级 null，不影响主体）
        info.setRequestBodySchema(resolveRequestBodySchema(handlerMethod));
        info.setResponseSchema(TypeSchemaResolver.resolve(method.getGenericReturnType()));
        // 接口描述：读取自有 @ApiDoc 注解
        info.setSummary(TypeSchemaResolver.operationSummary(method.getAnnotations()));
        info.setOperationDescription(TypeSchemaResolver.operationDescription(method.getAnnotations()));
        // 模块分组：读取 Controller 上的 @ApiModule 注解，无则用类简名兜底
        Class<?> controllerType = handlerMethod.getBeanType();
        info.setTag(TypeSchemaResolver.tagName(controllerType.getAnnotations(), controllerType.getName()));
        info.setTagDescription(TypeSchemaResolver.tagDescription(controllerType.getAnnotations()));
        // 响应契约：读取自有 @ApiResponse（按状态码分条）
        info.setResponses(TypeSchemaResolver.responses(method));
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

    /**
     * 排除规则：过滤不属于宿主业务契约的端点。
     * 依次排除 Spring 默认错误控制器、Archimedes 自身端点（base-path 下）、/error 错误页；
     * 若配置了 base-packages 白名单，则仅保留包前缀命中的控制器。
     */
    private boolean isExcluded(ApiInfo info) {
        String controllerClass = info.getControllerClass();
        // Spring Boot 自带的 BasicErrorController 非业务接口，恒排除
        if (controllerClass.contains("BasicErrorController")) {
            return true;
        }
        String basePath = properties.getBasePath();
        // Archimedes 自身的 UI/API 端点挂在 base-path 下，避免自我扫描
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
        // 配置了包白名单时，控制器类不在任一前缀下即排除
        if (basePackages != null && !basePackages.isEmpty()) {
            return basePackages.stream().noneMatch(controllerClass::startsWith);
        }
        return false;
    }

    /** 逐个提取 handler 方法参数：先注入参数名探测器（供无注解 name 时兜底），再转 ParamInfo。 */
    private List<ParamInfo> extractParams(HandlerMethod handlerMethod) {
        List<ParamInfo> params = new ArrayList<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            parameter.initParameterNameDiscovery(PARAM_NAME_DISCOVERER);
            params.add(toParamInfo(parameter));
        }
        return params;
    }

    /**
     * 单参数 → ParamInfo：先按绑定注解定来源/解析名/绑定必填，
     * 再套自有 @ApiParam（参数级优先，方法级按 name 匹配）决定说明/示例/必填——
     * 必填取「绑定注解必填 或 @ApiParam(required=true)」的并集（@ApiParam 只能上调不能降级）；
     * 说明/示例命中时取 @ApiParam，否则空串。
     */
    private ParamInfo toParamInfo(MethodParameter parameter) {
        String type = parameter.getGenericParameterType().getTypeName();
        java.util.Map<String, Object> validation = TypeSchemaResolver.paramValidation(parameter.getParameterAnnotations());

        // 1) 定来源、解析名、绑定注解必填
        ParamSource source;
        String name;
        boolean bindingRequired;
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        if (requestParam != null) {
            source = ParamSource.QUERY;
            name = firstNonEmpty(requestParam.name(), requestParam.value(), fallbackName(parameter));
            bindingRequired = requestParam.required();
        } else if (pathVariable != null) {
            source = ParamSource.PATH;
            name = firstNonEmpty(pathVariable.name(), pathVariable.value(), fallbackName(parameter));
            bindingRequired = pathVariable.required();
        } else if (requestHeader != null) {
            source = ParamSource.HEADER;
            name = firstNonEmpty(requestHeader.name(), requestHeader.value(), fallbackName(parameter));
            bindingRequired = requestHeader.required();
        } else if (requestBody != null) {
            source = ParamSource.BODY;
            name = fallbackName(parameter);
            bindingRequired = requestBody.required();
        } else {
            source = ParamSource.OTHER;
            name = fallbackName(parameter);
            bindingRequired = false;
        }

        // 2) 套 @ApiParam：命中则说明/示例取注解、未命中空串；必填对绑定注解只上调不降级
        ApiParam apiParam = TypeSchemaResolver.paramApiParam(
                parameter.getMethod(), parameter.getParameterAnnotations(), name);
        String description = apiParam != null ? apiParam.value() : "";
        String example = apiParam != null ? apiParam.example() : "";
        // @ApiParam 只能把参数上调为必填（其 required 默认 false，不能把绑定注解已确定的必填静默降为可选）
        boolean required = (apiParam != null && apiParam.required()) || bindingRequired;

        // 3) 组装
        ParamInfo pi = new ParamInfo(name, source, type, required, description);
        pi.setValidation(validation);
        pi.setExample(example);
        return pi;
    }

    /** 返回第一个非空字符串；全空返回空串。用于"注解 name → 注解 value → 参数名"的优先级回退。 */
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** 兜底参数名：取编译保留的参数名；探测不到（未开 -parameters 且无调试信息）时用 argN 占位。 */
    private static String fallbackName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
