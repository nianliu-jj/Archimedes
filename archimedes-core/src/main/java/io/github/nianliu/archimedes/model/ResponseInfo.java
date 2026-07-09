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

    public ResponseInfo() {
    }

    public ResponseInfo(int code, String description, String type, FieldInfo schema) {
        this.code = code;
        this.description = description;
        this.type = type;
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
}
