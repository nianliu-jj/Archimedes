package org.springframework.cloud.context.environment;

import org.springframework.context.ApplicationEvent;

import java.util.Set;

/**
 * Spring Cloud {@code EnvironmentChangeEvent} 的同 FQCN 测试桩：
 * 仅存在于测试 classpath，用于驱动 DynamicConfigManager 的反射发布路径
 * （与 TR 扫描器的桩注解策略一致——主代码零 spring-cloud 编译依赖）。
 * 构造器签名与真实类保持一致：{@code EnvironmentChangeEvent(Set<String>)}。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class EnvironmentChangeEvent extends ApplicationEvent {

    private final Set<String> keys;

    public EnvironmentChangeEvent(Set<String> keys) {
        super(keys);
        this.keys = keys;
    }

    public Set<String> getKeys() {
        return keys;
    }
}
