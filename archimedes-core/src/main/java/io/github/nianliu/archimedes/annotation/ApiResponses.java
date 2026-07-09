package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 响应声明容器：统一管理标注在方法上的多个 {@link ApiResponse}（{@link ApiResponse} 的重复容器）。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiResponses {

    /** 方法上的响应声明集合。 */
    ApiResponse[] value();
}
