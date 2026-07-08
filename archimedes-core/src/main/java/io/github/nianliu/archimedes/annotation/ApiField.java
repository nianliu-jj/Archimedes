package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在方法参数与 POJO 字段上，描述该参数/字段。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiField {

    /** 参数/字段说明。 */
    String value() default "";

    /** 是否必填。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
