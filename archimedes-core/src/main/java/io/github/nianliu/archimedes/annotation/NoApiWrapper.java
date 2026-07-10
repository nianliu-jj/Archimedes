package io.github.nianliu.archimedes.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 REST handler 方法或 Controller 类上，声明该接口（或整个控制器）
 * 的响应<b>不被统一响应包装体</b>包裹——契约展示时 responseSchema 保持方法真实返回类型，
 * 不套 {@code archimedes.api.response-wrapper.wrapper-class} 指定的外壳。
 * <p>语义等同宿主项目里常见的 {@code @NotControllerResponseAdvice}：用于本就直接返回
 * 包装体、或需绕过统一包装的端点。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoApiWrapper {
}
