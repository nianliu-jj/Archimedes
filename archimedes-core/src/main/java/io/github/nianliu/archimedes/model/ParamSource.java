package io.github.nianliu.archimedes.model;

/**
 * 参数来源枚举，标识接口参数的绑定位置。FORM 为后续切片预留，切片一不产出。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public enum ParamSource {
    /** URL 查询串参数（@RequestParam）。 */
    QUERY,
    /** 路径变量（@PathVariable）。 */
    PATH,
    /** 请求体（@RequestBody）。 */
    BODY,
    /** 请求头（@RequestHeader）。 */
    HEADER,
    /** 表单字段（预留，暂未产出）。 */
    FORM,
    /** 其他/无法归类来源。 */
    OTHER
}
