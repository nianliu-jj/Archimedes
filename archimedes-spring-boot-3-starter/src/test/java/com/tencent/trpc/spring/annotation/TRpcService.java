package com.tencent.trpc.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 测试桩：与 tRPC-Java 真实注解同 FQCN 的最小版本（仅测试源码，不随 jar 发布）。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TRpcService {

    String name() default "";

    String version() default "";

    String group() default "";
}
