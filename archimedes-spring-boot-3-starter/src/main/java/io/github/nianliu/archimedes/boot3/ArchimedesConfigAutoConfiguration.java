package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.env.ConfigManagementProperties;
import io.github.nianliu.archimedes.env.ConfigurationPropertiesRebinder;
import io.github.nianliu.archimedes.env.DynamicConfigManager;
import io.github.nianliu.archimedes.env.EnvironmentConfigService;
import io.github.nianliu.archimedes.web.ArchimedesConfigController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * 配置中心自动装配（SB3）：注册配置枚举服务、动态配置管理器、属性 Bean 重绑定器
 * 与配置端点控制器。
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnWebApplication} 不限类型——控制器为纯注解式零 servlet 依赖，
 *       SERVLET 与 REACTIVE 栈同等生效；</li>
 *   <li>双开关（{@link ConfigCenterEnabled}）：{@code archimedes.api.enabled} 总开关
 *       与 {@code archimedes.config.enabled} 独立开关须同时开启（均默认开启）。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@AutoConfiguration
@ConditionalOnWebApplication
@Conditional(ArchimedesConfigAutoConfiguration.ConfigCenterEnabled.class)
@EnableConfigurationProperties(ConfigManagementProperties.class)
public class ArchimedesConfigAutoConfiguration {

    /** @ConfigurationProperties Bean 原地重绑定器：热更新后使属性 Bean 立即反映新值。 */
    @Bean
    public ConfigurationPropertiesRebinder archimedesConfigurationPropertiesRebinder(
            ConfigurableApplicationContext applicationContext) {
        return new ConfigurationPropertiesRebinder(applicationContext);
    }

    /** 动态配置管理器：热更新写入口（动态属性源 + 重绑定 + 事件发布）。 */
    @Bean
    public DynamicConfigManager archimedesDynamicConfigManager(
            ConfigurableApplicationContext applicationContext,
            ConfigurationPropertiesRebinder rebinder) {
        // 应用上下文同时充当 ApplicationEventPublisher
        return new DynamicConfigManager(applicationContext.getEnvironment(), rebinder, applicationContext);
    }

    /** 配置枚举服务：全属性源只读枚举 + 敏感值脱敏。 */
    @Bean
    public EnvironmentConfigService archimedesEnvironmentConfigService(
            ConfigurableApplicationContext applicationContext,
            ConfigManagementProperties properties) {
        return new EnvironmentConfigService(applicationContext.getEnvironment(), properties);
    }

    /** 配置中心端点控制器：GET {base-path}/config 与 POST {base-path}/config/update。 */
    @Bean
    public ArchimedesConfigController archimedesConfigController(
            EnvironmentConfigService configService,
            DynamicConfigManager dynamicConfigManager,
            ConfigManagementProperties properties) {
        return new ArchimedesConfigController(configService, dynamicConfigManager, properties);
    }

    /** 双开关组合条件：archimedes.api.enabled 与 archimedes.config.enabled 须同时开启。 */
    static class ConfigCenterEnabled extends AllNestedConditions {

        ConfigCenterEnabled() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
        static class ApiEnabled {
        }

        @ConditionalOnProperty(prefix = "archimedes.config", name = "enabled", matchIfMissing = true)
        static class ConfigCenterSwitch {
        }
    }
}
