package io.swagger.v3.oas.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试桩：与 Swagger v3 @Parameter 同 FQCN，驱动参数说明的反射读取路径。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface Parameter {

    String description() default "";
}
