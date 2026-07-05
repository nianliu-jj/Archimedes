package com.alipay.sofa.runtime.api.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 测试桩：与 SOFABoot 真实注解同 FQCN 的最小版本（仅测试源码，不随 jar 发布）。 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface SofaServiceBinding {

    String bindingType() default "bolt";
}
