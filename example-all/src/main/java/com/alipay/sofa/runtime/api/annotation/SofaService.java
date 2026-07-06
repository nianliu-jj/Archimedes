package com.alipay.sofa.runtime.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【桩注解】与 SOFABoot @SofaService 同 FQCN，用于在不引入 SOFA 依赖的前提下
 * 驱动 Archimedes 的零依赖反射扫描路径（真实宿主引入 sofa-boot 后行为一致）。
 * 仅保留扫描器读取的属性：interfaceType / uniqueId / bindings。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SofaService {

    /** 服务接口（契约 serviceName 的来源） */
    Class<?> interfaceType() default void.class;

    /** 服务唯一标识（契约 metadata.uniqueId） */
    String uniqueId() default "";

    /** 服务绑定协议列表（契约 metadata.bindings，TR 协议即 bindingType=tr） */
    SofaServiceBinding[] bindings() default {};
}
