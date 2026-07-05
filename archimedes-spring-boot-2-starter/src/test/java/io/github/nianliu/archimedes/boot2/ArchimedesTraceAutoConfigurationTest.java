package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesTraceAutoConfigurationTest {

    @Test
    void registersTraceBeansByDefault() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesTraceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceContextManager.class);
                    assertThat(context).hasBean("archimedesTraceIdFilter");
                });
    }

    @Test
    void skipsAllTraceBeansWhenDisabled() {
        new WebApplicationContextRunner()
                .withPropertyValues("archimedes.trace.enabled=false")
                .withConfiguration(AutoConfigurations.of(ArchimedesTraceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TraceContextManager.class);
                    assertThat(context).doesNotHaveBean("archimedesTraceIdFilter");
                });
    }
}
