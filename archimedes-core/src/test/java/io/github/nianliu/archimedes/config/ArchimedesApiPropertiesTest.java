package io.github.nianliu.archimedes.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaults() {
        runner.run(context -> {
            ArchimedesApiProperties props = context.getBean(ArchimedesApiProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getBasePath()).isEqualTo("/archimedes");
            assertThat(props.isUiEnabled()).isTrue();
            assertThat(props.getBasePackages()).isEmpty();
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                "archimedes.api.enabled=false",
                "archimedes.api.base-path=/custom",
                "archimedes.api.ui-enabled=false",
                "archimedes.api.base-packages=com.a,com.b"
        ).run(context -> {
            ArchimedesApiProperties props = context.getBean(ArchimedesApiProperties.class);
            assertThat(props.isEnabled()).isFalse();
            assertThat(props.getBasePath()).isEqualTo("/custom");
            assertThat(props.isUiEnabled()).isFalse();
            assertThat(props.getBasePackages()).containsExactly("com.a", "com.b");
        });
    }

    @EnableConfigurationProperties(ArchimedesApiProperties.class)
    static class TestConfig {
    }
}
