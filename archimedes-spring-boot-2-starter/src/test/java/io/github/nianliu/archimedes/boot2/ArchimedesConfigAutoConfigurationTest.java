package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.env.ConfigManagementProperties;
import io.github.nianliu.archimedes.env.DynamicConfigManager;
import io.github.nianliu.archimedes.env.EnvironmentConfigService;
import io.github.nianliu.archimedes.web.ArchimedesConfigController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesConfigAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ArchimedesConfigAutoConfiguration.class));

    @Test
    void registersConfigBeansByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EnvironmentConfigService.class);
            assertThat(context).hasSingleBean(DynamicConfigManager.class);
            assertThat(context).hasSingleBean(ArchimedesConfigController.class);
            assertThat(context).hasSingleBean(ConfigManagementProperties.class);
        });
    }

    @Test
    void skipsWhenConfigSwitchOff() {
        runner.withPropertyValues("archimedes.config.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ArchimedesConfigController.class));
    }

    @Test
    void skipsWhenApiTotalSwitchOff() {
        runner.withPropertyValues("archimedes.api.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ArchimedesConfigController.class));
    }

    @Test
    void skipsInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesConfigAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ArchimedesConfigController.class));
    }
}
