package io.github.nianliu.archimedes.env;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigManagementPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaults() {
        runner.run(context -> {
            ConfigManagementProperties props = context.getBean(ConfigManagementProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.isHotRefreshEnabled()).isTrue();
            assertThat(props.getSensitiveKeys())
                    .containsExactly("password", "secret", "token", "credential", "key");
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                "archimedes.config.enabled=false",
                "archimedes.config.hot-refresh-enabled=false",
                "archimedes.config.sensitive-keys=internal,private"
        ).run(context -> {
            ConfigManagementProperties props = context.getBean(ConfigManagementProperties.class);
            assertThat(props.isEnabled()).isFalse();
            assertThat(props.isHotRefreshEnabled()).isFalse();
            // 用户配置整体替换默认关键字集合
            assertThat(props.getSensitiveKeys()).containsExactly("internal", "private");
        });
    }

    @EnableConfigurationProperties(ConfigManagementProperties.class)
    static class TestConfig {
    }
}
