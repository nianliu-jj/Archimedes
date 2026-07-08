package io.github.nianliu.archimedes.model;

import java.util.List;
import java.util.Map;

/**
 * 请求体/响应体的字段结构节点（递归树）：供 UI 生成示例 JSON 预填与
 * 字段说明表展示。根节点 name 为空串、type 为解包后的类型简名。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class FieldInfo {

    /** 字段名（Jackson @JsonProperty 可覆盖；根节点为空串）。 */
    private String name;

    /** 类型展示串（简名，如 String、List&lt;OrderItem&gt;、Map&lt;String,User&gt;）。 */
    private String type;

    /** 是否必填（自有 @ApiField#required 标记）。 */
    private boolean required;

    /** 字段说明（自有 @ApiField 读取；枚举自动补可选值；可为空串）。 */
    private String description;

    /** 是否为集合/数组语义（children 描述其元素结构）。 */
    private boolean array;

    /** 枚举可选值列表（非枚举字段为 null；UI 渲染为下拉框供用户选择，避免手动输入出错）。 */
    private List<String> enumValues;

    /** 前端校验规则（validation 注解提取：pattern/min/max/minLength/maxLength；无则为 null）。 */
    private Map<String, Object> validation;

    /** 嵌套子字段；叶子节点为空列表。 */
    private List<FieldInfo> children;

    public FieldInfo() {
    }

    public FieldInfo(String name, String type, boolean required, String description,
                     boolean array, List<FieldInfo> children) {
        this(name, type, required, description, array, null, children);
    }

    public FieldInfo(String name, String type, boolean required, String description,
                     boolean array, List<String> enumValues, List<FieldInfo> children) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.array = array;
        this.enumValues = enumValues;
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isArray() {
        return array;
    }

    public void setArray(boolean array) {
        this.array = array;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    public Map<String, Object> getValidation() {
        return validation;
    }

    public void setValidation(Map<String, Object> validation) {
        this.validation = validation;
    }

    public List<FieldInfo> getChildren() {
        return children;
    }

    public void setChildren(List<FieldInfo> children) {
        this.children = children;
    }
}
