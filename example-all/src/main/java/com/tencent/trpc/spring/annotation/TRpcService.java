package com.tencent.trpc.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【桩注解】与腾讯 tRPC-Java @TRpcService 同 FQCN，驱动零依赖反射扫描路径
 * （真实宿主引入 trpc-spring 后行为一致）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TRpcService {

    /** 服务名（契约 metadata.name） */
    String name() default "";

    /** 版本（契约 version） */
    String version() default "";

    /** 分组（契约 group） */
    String group() default "";
}
