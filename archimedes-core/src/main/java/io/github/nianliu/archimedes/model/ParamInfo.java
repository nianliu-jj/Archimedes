package io.github.nianliu.archimedes.model;

/**
 * 单个接口参数的契约信息：参数名、绑定来源、类型、是否必填与说明。
 * <p>由 REST 扫描器结合方法参数上的绑定注解（@RequestParam/@PathVariable/@RequestBody 等）
 * 与 Swagger 注解反射生成，供 UI 渲染参数表与在线调试表单。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class ParamInfo {

    /** 参数名。 */
    private String name;
    /** 参数绑定来源（QUERY/PATH/BODY/HEADER 等）。 */
    private ParamSource source;
    /** 参数类型展示串（简名）。 */
    private String type;
    /** 是否必填。 */
    private boolean required;
    /** 参数说明（Swagger @Parameter/@ApiParam 反射读取，可为空串）。 */
    private String description;
    /** 前端校验规则（validation 注解提取：pattern/min/max/minLength/maxLength；无则为 null）。 */
    private java.util.Map<String, Object> validation;

    public ParamInfo() {
    }

    /** 便捷构造：说明缺省为空串。 */
    public ParamInfo(String name, ParamSource source, String type, boolean required) {
        this(name, source, type, required, "");
    }

    /** 全量构造。 */
    public ParamInfo(String name, ParamSource source, String type, boolean required, String description) {
        this.name = name;
        this.source = source;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ParamSource getSource() {
        return source;
    }

    public void setSource(ParamSource source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public java.util.Map<String, Object> getValidation() {
        return validation;
    }

    public void setValidation(java.util.Map<String, Object> validation) {
        this.validation = validation;
    }
}
