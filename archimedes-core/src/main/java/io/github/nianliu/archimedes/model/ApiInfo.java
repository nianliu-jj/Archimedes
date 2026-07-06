package io.github.nianliu.archimedes.model;

import java.util.List;

public class ApiInfo {

    private String controllerClass;
    private String handlerMethod;
    private List<String> httpMethods;
    private List<String> paths;
    private List<ParamInfo> params;
    private String returnType;
    private List<String> consumes;
    private List<String> produces;
    private boolean deprecated;
    /** 首个 @RequestBody 参数类型的字段结构树；无 BODY 参数或解析失败时为 null。 */
    private FieldInfo requestBodySchema;
    /** 返回类型（解包 ResponseEntity/Mono 等后）的字段结构树；void 或解析失败时为 null。 */
    private FieldInfo responseSchema;

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
}
