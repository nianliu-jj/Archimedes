package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述一个 REST 请求参数。可标注在参数前，或标注在方法上（此时 {@link #name()} 必须与参数名匹配才命中）。
 * <p>设为 {@link Repeatable}，方法上可直接连写多个，由 Java 自动聚合进 {@link ApiParams}。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiParams.class)
public @interface ApiParam {

    /** 参数名。标在方法上时必须等于参数名才命中；标在参数前时可省略。 */
    String name() default "";

    /** 参数说明。 */
    String value() default "";

    /** 是否必填（页面必填列的来源）。 */
    boolean required() default false;

    /** 示例值（供 UI 在线调试预填）。 */
    String example() default "";
}
