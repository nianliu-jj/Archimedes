package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述一条 REST 响应（按 HTTP 状态码分条）。设为 {@link Repeatable}，方法上可连写多个描述不同状态码。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiResponses.class)
public @interface ApiResponse {

    /** HTTP 状态码。 */
    int code() default 200;

    /** 响应说明。 */
    String description() default "";

    /** 响应体类型；非 {@code Void.class} 时解析其字段结构树展示。 */
    Class<?> type() default Void.class;

    /** 响应示例。 */
    String example() default "";
}
