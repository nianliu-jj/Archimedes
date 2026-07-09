package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 POJO 字段上，描述该字段（请求体/响应体结构树的说明与必填）。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiField {

    /** 字段说明。 */
    String value() default "";

    /** 是否必填。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
