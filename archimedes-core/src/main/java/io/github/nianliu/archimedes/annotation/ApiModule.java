package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller / RPC 服务接口类上，描述其所属模块（分组）。
 * <p>{@link #name()} 为空时回退 {@link #value()}，供前端按模块聚合展示。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiModule {

    /** 模块名（{@link #name()} 的别名，二选一）。 */
    String value() default "";

    /** 模块名（优先于 {@link #value()}）。 */
    String name() default "";

    /** 模块描述。 */
    String description() default "";
}
