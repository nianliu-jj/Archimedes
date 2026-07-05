package io.github.nianliu.archimedes.boot3;

import ch.qos.logback.classic.LoggerContext;
import io.github.nianliu.archimedes.log.InMemoryLogStore;
import io.github.nianliu.archimedes.log.LogCaptureInitializer;
import io.github.nianliu.archimedes.log.LogCaptureProperties;
import io.github.nianliu.archimedes.log.LogStore;
import io.github.nianliu.archimedes.trace.TraceProperties;
import io.github.nianliu.archimedes.web.ArchimedesLogController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(prefix = "archimedes.log.capture", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({LogCaptureProperties.class, TraceProperties.class})
public class ArchimedesLogAutoConfiguration {

    /** ES 等持久化实现的让位点：宿主注册自己的 LogStore Bean 即替换内存默认实现。 */
    @Bean
    @ConditionalOnMissingBean(LogStore.class)
    public LogStore archimedesLogStore(LogCaptureProperties properties) {
        return new InMemoryLogStore(properties.getMaxEntries(), properties.getMaxEntriesPerTrace());
    }

    @Bean
    public LogCaptureInitializer archimedesLogCaptureInitializer(LogStore logStore,
                                                                 TraceProperties traceProperties) {
        return new LogCaptureInitializer(logStore, traceProperties);
    }

    @Bean
    public ArchimedesLogController archimedesLogController(LogStore logStore,
                                                           TraceProperties traceProperties) {
        return new ArchimedesLogController(logStore, traceProperties);
    }
}
