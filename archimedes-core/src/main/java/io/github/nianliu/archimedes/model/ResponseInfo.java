package io.github.nianliu.archimedes.model;

/**
 * 单条 REST 响应契约（对应一个 HTTP 状态码），来自自有 {@code @ApiResponse} 注解。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
public class ResponseInfo {

    /** HTTP 状态码。 */
    private int code;
    /** 响应说明。 */
    private String description;
    /** 响应体类型展示串（简名）；Void 时为 null。 */
    private String type;
    /** 响应体字段结构树；type 为 Void 或解析失败时为 null。 */
    private FieldInfo schema;
    /** 响应示例（来自自有 {@code @ApiResponse#example}，可为空串）。 */
    private String example;

    public ResponseInfo() {
    }

    /** 4 参构造：不带示例，委托 5 参构造并传 example=null，保持既有调用兼容。 */
    public ResponseInfo(int code, String description, String type, FieldInfo schema) {
        this(code, description, type, null, schema);
    }

    /** 5 参构造：example 位于 schema 之前。 */
    public ResponseInfo(int code, String description, String type, String example, FieldInfo schema) {
        this.code = code;
        this.description = description;
        this.type = type;
        this.example = example;
        this.schema = schema;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FieldInfo getSchema() {
        return schema;
    }

    public void setSchema(FieldInfo schema) {
        this.schema = schema;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }
}
