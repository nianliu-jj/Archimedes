package io.swagger.v3.oas.annotations.media;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试桩：与 Swagger v3 注解同 FQCN，驱动 TypeSchemaResolver 的零依赖反射读取路径。
 * requiredMode 以 String 表达（真实注解为枚举，反射读取侧 String.valueOf 行为一致）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface Schema {

    String description() default "";

    String requiredMode() default "AUTO";

    boolean required() default false;
}
