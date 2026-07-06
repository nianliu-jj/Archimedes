package com.alipay.sofa.runtime.api.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【桩注解】与 SOFABoot @SofaServiceBinding 同 FQCN（配合 {@link SofaService#bindings()}）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface SofaServiceBinding {

    /** 绑定协议类型：tr / bolt / rest 等 */
    String bindingType() default "";
}
