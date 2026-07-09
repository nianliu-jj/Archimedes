package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级参数说明容器：统一管理标注在方法上的多个 {@link ApiParam}（{@link ApiParam} 的重复容器）。
 *
 * @author nianliu-jj
 * @since 2026-07-09
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiParams {

    /** 方法上的参数说明集合。 */
    ApiParam[] value();
}
