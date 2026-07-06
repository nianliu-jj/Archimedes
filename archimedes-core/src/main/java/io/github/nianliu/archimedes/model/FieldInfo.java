package io.github.nianliu.archimedes.model;

import java.util.List;

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

    /** 是否必填（validation @NotNull/@NotBlank/@NotEmpty 或 Swagger required 标记）。 */
    private boolean required;

    /** 字段说明（Swagger v3/v2 或 Jackson 注解反射读取；枚举自动补可选值；可为空串）。 */
    private String description;

    /** 是否为集合/数组语义（children 描述其元素结构）。 */
    private boolean array;

    /** 嵌套子字段；叶子节点为空列表。 */
    private List<FieldInfo> children;

    public FieldInfo() {
    }

    public FieldInfo(String name, String type, boolean required, String description,
                     boolean array, List<FieldInfo> children) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.array = array;
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

    public List<FieldInfo> getChildren() {
        return children;
    }

    public void setChildren(List<FieldInfo> children) {
        this.children = children;
    }
}
