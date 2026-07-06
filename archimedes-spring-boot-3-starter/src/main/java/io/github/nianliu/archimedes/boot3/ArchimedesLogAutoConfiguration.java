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

/**
 * 日志采集自动装配（SERVLET 栈）：将应用日志按 traceId 归集到可查询的 LogStore，
 * 并暴露内置日志查询控制器。基于 Logback，故以 LoggerContext 存在为装配前提。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@AutoConfiguration
// 仅 Servlet 栈装配（与 trace 采集同栈，依赖请求级 traceId 关联日志）
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 采集实现挂接 Logback，classpath 有 LoggerContext 才装配
@ConditionalOnClass(LoggerContext.class)
// 允许通过 archimedes.log.capture.enabled=false 关闭；缺省开启
@ConditionalOnProperty(prefix = "archimedes.log.capture", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({LogCaptureProperties.class, TraceProperties.class})
public class ArchimedesLogAutoConfiguration {

    /** ES 等持久化实现的让位点：宿主注册自己的 LogStore Bean 即替换内存默认实现。 */
    @Bean
    @ConditionalOnMissingBean(LogStore.class)
    public LogStore archimedesLogStore(LogCaptureProperties properties) {
        return new InMemoryLogStore(properties.getMaxEntries(), properties.getMaxEntriesPerTrace());
    }

    /** 日志采集初始化器：向 Logback 挂接 appender，把日志事件按 traceId 写入 LogStore。 */
    @Bean
    public LogCaptureInitializer archimedesLogCaptureInitializer(LogStore logStore,
                                                                 TraceProperties traceProperties) {
        return new LogCaptureInitializer(logStore, traceProperties);
    }

    /** 内置日志查询控制器：按 traceId 等条件从 LogStore 检索日志，供内置 UI 调用。 */
    @Bean
    public ArchimedesLogController archimedesLogController(LogStore logStore,
                                                           TraceProperties traceProperties) {
        return new ArchimedesLogController(logStore, traceProperties);
    }
}
