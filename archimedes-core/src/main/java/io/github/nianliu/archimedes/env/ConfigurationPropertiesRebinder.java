package io.github.nianliu.archimedes.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code @ConfigurationProperties} Bean 的轻量级重绑定器：配置热更新后，
 * 对 prefix 命中变更 key 的属性 Bean 用 {@link Binder} 在<b>既有实例</b>上重新绑定，
 * 使其立即反映新配置值。
 * <p>与"销毁 + 重建 Bean"式的重绑定方案相比：零新增依赖、不替换实例
 * （既有引用继续有效）；代价是构造器绑定的不可变 Bean 无法刷新
 * ——检测到无可写属性时防御式跳过并 WARN。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class ConfigurationPropertiesRebinder {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationPropertiesRebinder.class);

    private final ConfigurableApplicationContext applicationContext;

    public ConfigurationPropertiesRebinder(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 对 prefix 命中任一变更 key 的 @ConfigurationProperties Bean 执行原地重绑定。
     *
     * @param changedKeys 本次变更的配置键集合
     * @return 成功重绑定的 Bean 名称列表（跳过与失败的不在其中）
     */
    public List<String> rebind(Set<String> changedKeys) {
        List<String> refreshed = new ArrayList<>();
        if (changedKeys == null || changedKeys.isEmpty()) {
            return refreshed;
        }
        // getBeansWithAnnotation 覆盖类级注解与 @Bean 工厂方法注解两种注册方式
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ConfigurationProperties.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();
            // findAnnotationOnBean 同时检查 Bean 类与工厂方法，拿到合并后的注解属性
            ConfigurationProperties annotation =
                    applicationContext.getBeanFactory().findAnnotationOnBean(beanName, ConfigurationProperties.class);
            if (annotation == null) {
                continue;
            }
            String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
            if (!matchesPrefix(prefix, changedKeys)) {
                continue;
            }
            // 构造器绑定的不可变 Bean（无任何可写属性）无法原地刷新，跳过并提示
            Class<?> targetType = ClassUtils.getUserClass(bean);
            if (!hasWritableProperty(targetType)) {
                log.warn("Bean {} (prefix={}) 为构造器绑定或无可写属性，跳过热更新重绑定；如需刷新请重启或改用 JavaBean 风格",
                        beanName, prefix);
                continue;
            }
            try {
                Binder.get(applicationContext.getEnvironment())
                        .bind(prefix, Bindable.ofInstance(bean));
                refreshed.add(beanName);
                log.info("配置热更新已重绑定 @ConfigurationProperties Bean: {} (prefix={})", beanName, prefix);
            } catch (Exception ex) {
                // 单个 Bean 绑定失败（如新值类型不合法）不影响其余 Bean
                log.warn("Bean {} (prefix={}) 重绑定失败，跳过: {}", beanName, prefix, ex.getMessage());
            }
        }
        return refreshed;
    }

    /** prefix 命中判定：空 prefix 绑定根命名空间视为全命中；否则 key 等于 prefix 或位于其命名空间下。 */
    private boolean matchesPrefix(String prefix, Set<String> changedKeys) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        for (String key : changedKeys) {
            if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /** 是否存在至少一个可写 JavaBean 属性（有 setter 才能原地重绑定）。 */
    private boolean hasWritableProperty(Class<?> type) {
        for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(type)) {
            if (descriptor.getWriteMethod() != null) {
                return true;
            }
        }
        return false;
    }
}
