package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.ReactiveRestApiScanner;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REACTIVE 分支条件装配单测：响应式上下文装配、SERVLET/非 Web 让位、
 * 纯 reactive 类路径（隐藏 servlet 类）仍正常。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
class ArchimedesReactiveAutoConfigurationTest {

    @Test
    void registersReactiveScannerAndControllerInReactiveWebApp() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesReactiveAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveRestApiScanner.class);
                    assertThat(context).hasSingleBean(ArchimedesApiController.class);
                    assertThat(context).hasSingleBean(ArchimedesApiProperties.class);
                });
    }

    @Test
    void worksWithoutServletClassesOnClasspath() {
        new ReactiveWebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(
                        org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class))
                .withConfiguration(AutoConfigurations.of(ArchimedesReactiveAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveRestApiScanner.class);
                    assertThat(context).hasSingleBean(ArchimedesApiController.class);
                });
    }

    @Test
    void skipsInServletWebApp() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesReactiveAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ReactiveRestApiScanner.class));
    }

    @Test
    void skipsInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesReactiveAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ReactiveRestApiScanner.class));
    }

    @Test
    void skipsWhenDisabled() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesReactiveAutoConfiguration.class))
                .withPropertyValues("archimedes.api.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReactiveRestApiScanner.class));
    }
}
