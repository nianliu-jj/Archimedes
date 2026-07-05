package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.log.LogEntry;
import io.github.nianliu.archimedes.log.LogQueryResult;
import io.github.nianliu.archimedes.log.LogStore;
import io.github.nianliu.archimedes.web.ArchimedesLogController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesLogAutoConfigurationTest {

    @Test
    void registersLogBeansByDefault() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesLogAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(LogStore.class);
                    assertThat(context).hasSingleBean(ArchimedesLogController.class);
                    assertThat(context).hasBean("archimedesLogCaptureInitializer");
                });
    }

    @Test
    void skipsAllWhenCaptureDisabled() {
        new WebApplicationContextRunner()
                .withPropertyValues("archimedes.log.capture.enabled=false")
                .withConfiguration(AutoConfigurations.of(ArchimedesLogAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LogStore.class);
                    assertThat(context).doesNotHaveBean(ArchimedesLogController.class);
                });
    }

    @Test
    void customLogStoreBeanTakesPrecedence() {
        new WebApplicationContextRunner()
                .withUserConfiguration(CustomStoreConfig.class)
                .withConfiguration(AutoConfigurations.of(ArchimedesLogAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(LogStore.class);
                    assertThat(context.getBean(LogStore.class)).isInstanceOf(CustomStore.class);
                });
    }

    @Configuration
    static class CustomStoreConfig {
        @Bean
        LogStore customStore() {
            return new CustomStore();
        }
    }

    static class CustomStore implements LogStore {
        @Override
        public void append(LogEntry entry) {
        }

        @Override
        public LogQueryResult queryByTraceId(String traceId, int page, int size) {
            return new LogQueryResult(traceId, 0, page, size, Collections.emptyList());
        }
    }
}
