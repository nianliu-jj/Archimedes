package io.github.nianliu.archimedes.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.github.nianliu.archimedes.trace.TraceProperties;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * logback 在场时把采集 Appender 编程式挂到 root logger（销毁时卸载）；
 * 非 logback 日志实现打 INFO 说明并跳过，不影响应用其余功能。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class LogCaptureInitializer implements InitializingBean, DisposableBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LogCaptureInitializer.class);

    /** 采集到的日志的落地存储。 */
    private final LogStore store;
    /** trace 配置，用于取 MDC 中 traceId/spanId 的键名。 */
    private final TraceProperties traceProperties;

    /** 已挂载 Appender 的 logback 上下文（销毁时据此卸载，非 logback 时为 null）。 */
    private LoggerContext attachedContext;
    /** 编程式创建的采集 Appender 实例。 */
    private ArchimedesLogAppender appender;

    public LogCaptureInitializer(LogStore store, TraceProperties traceProperties) {
        this.store = store;
        this.traceProperties = traceProperties;
    }

    /**
     * Bean 初始化后挂载 Appender：仅当 SLF4J 绑定的是 logback 时才介入，
     * 否则打印 INFO 提示并优雅跳过（避免强依赖 logback）。
     */
    @Override
    public void afterPropertiesSet() {
        // 只有绑定实现确为 logback 才能拿到 LoggerContext 并挂 Appender
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            log.info("Archimedes: 当前日志实现不是 logback（{}），链路日志采集不可用，其余功能不受影响",
                    factory.getClass().getName());
            return;
        }
        LoggerContext context = (LoggerContext) factory;
        // 用 trace 配置的 MDC 键名构造 Appender，并按 logback 规范 setContext + start 后再挂载
        appender = new ArchimedesLogAppender(store, traceProperties.getMdcKey(), traceProperties.getSpanIdKey());
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        attachedContext = context;
        log.debug("Archimedes: 链路日志采集 Appender 已挂载 root logger");
    }

    /** Bean 销毁时从 root logger 卸载并停止 Appender，避免上下文泄漏。 */
    @Override
    public void destroy() {
        if (attachedContext != null && appender != null) {
            attachedContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
    }
}
