package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import io.github.nianliu.archimedes.trace.propagation.MdcExecutorBeanPostProcessor;
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
                    assertThat(context).hasSingleBean(MdcExecutorBeanPostProcessor.class);
                });
    }

    @Test
    void skipsPropagationWhenDisabled() {
        new WebApplicationContextRunner()
                .withPropertyValues("archimedes.trace.propagation.enabled=false")
                .withConfiguration(AutoConfigurations.of(ArchimedesTraceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceContextManager.class);
                    assertThat(context).doesNotHaveBean(MdcExecutorBeanPostProcessor.class);
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
