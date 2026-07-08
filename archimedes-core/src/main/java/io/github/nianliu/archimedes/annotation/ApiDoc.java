package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 REST handler / RPC 方法 / WebSocket handler 方法上，描述该接口。
 * <p>{@link #summary()} 为空时回退 {@link #value()}。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDoc {

    /** 接口摘要（{@link #value()} 的别名）。 */
    String summary() default "";

    /** 接口摘要（别名，与 {@link #summary()} 二选一）。 */
    String value() default "";

    /** 接口详细描述。 */
    String description() default "";

    /** 是否弃用（与 {@code @Deprecated} 取或）。 */
    boolean deprecated() default false;
}
