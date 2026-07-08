package io.github.nianliu.archimedes.model;

import java.util.List;

/**
 * 单个 REST 接口（一个 handler 方法）的契约信息。
 * <p>由 REST 扫描器从 Spring MVC/WebFlux 的 HandlerMapping 反射提取，
 * 涵盖请求映射（方法、路径）、参数、返回类型、内容协商（consumes/produces）、
 * 弃用标记，以及请求/响应体的字段结构树。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class ApiInfo {

    /** 所属 Controller 全限定类名。 */
    private String controllerClass;
    /** 处理方法签名（用于在 UI 中定位来源）。 */
    private String handlerMethod;
    /** 支持的 HTTP 方法（GET/POST...）；未显式限定时可能为空表示全部。 */
    private List<String> httpMethods;
    /** 映射路径 pattern 列表（含类级 + 方法级组合）。 */
    private List<String> paths;
    /** 参数契约列表。 */
    private List<ParamInfo> params;
    /** 返回类型展示串（已解包 ResponseEntity/Mono 等容器）。 */
    private String returnType;
    /** 可接受的请求内容类型（对应 @RequestMapping#consumes）。 */
    private List<String> consumes;
    /** 可产出的响应内容类型（对应 @RequestMapping#produces）。 */
    private List<String> produces;
    /** 是否标注 @Deprecated。 */
    private boolean deprecated;
    /** 首个 @RequestBody 参数类型的字段结构树；无 BODY 参数或解析失败时为 null。 */
    private FieldInfo requestBodySchema;
    /** 返回类型（解包 ResponseEntity/Mono 等后）的字段结构树；void 或解析失败时为 null。 */
    private FieldInfo responseSchema;
    /** 接口摘要（来自自有 @ApiDoc#summary，可为空）。 */
    private String summary;
    /** 接口详细描述（来自自有 @ApiDoc#description，可为空）。 */
    private String operationDescription;
    /** 所属模块标签（Controller 维度，来自 @ApiModule#name，用于前端按模块分组；可为空则按类简名兜底）。 */
    private String tag;
    /** 模块描述（来自 @ApiModule#description，可为空）。 */
    private String tagDescription;

    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }

    public List<String> getHttpMethods() {
        return httpMethods;
    }

    public void setHttpMethods(List<String> httpMethods) {
        this.httpMethods = httpMethods;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<ParamInfo> getParams() {
        return params;
    }

    public void setParams(List<ParamInfo> params) {
        this.params = params;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public void setConsumes(List<String> consumes) {
        this.consumes = consumes;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public FieldInfo getRequestBodySchema() {
        return requestBodySchema;
    }

    public void setRequestBodySchema(FieldInfo requestBodySchema) {
        this.requestBodySchema = requestBodySchema;
    }

    public FieldInfo getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(FieldInfo responseSchema) {
        this.responseSchema = responseSchema;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getOperationDescription() {
        return operationDescription;
    }

    public void setOperationDescription(String operationDescription) {
        this.operationDescription = operationDescription;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTagDescription() {
        return tagDescription;
    }

    public void setTagDescription(String tagDescription) {
        this.tagDescription = tagDescription;
    }
}
