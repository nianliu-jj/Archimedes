package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import io.github.nianliu.archimedes.trace.TraceIdGenerator;
import io.github.nianliu.archimedes.trace.TraceIdResolver;
import io.github.nianliu.archimedes.trace.TraceProperties;
import io.github.nianliu.archimedes.trace.UuidTraceIdGenerator;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "archimedes.trace", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties.class)
public class ArchimedesTraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator archimedesTraceIdGenerator() {
        return new UuidTraceIdGenerator();
    }

    @Bean
    public TraceContextManager archimedesTraceContextManager(TraceProperties properties,
                                                             TraceIdGenerator generator,
                                                             ObjectProvider<TraceIdResolver> resolver) {
        return new TraceContextManager(properties, generator, resolver.getIfAvailable());
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> archimedesTraceIdFilter(TraceContextManager manager,
                                                                         TraceProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter(manager, properties));
        // trace 上下文必须先于一切业务 Filter 建立；异步二次分发交由跨线程传递机制处理
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns("/*");
        registration.setName("archimedesTraceIdFilter");
        return registration;
    }
}
