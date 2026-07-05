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
 */
public class LogCaptureInitializer implements InitializingBean, DisposableBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LogCaptureInitializer.class);

    private final LogStore store;
    private final TraceProperties traceProperties;

    private LoggerContext attachedContext;
    private ArchimedesLogAppender appender;

    public LogCaptureInitializer(LogStore store, TraceProperties traceProperties) {
        this.store = store;
        this.traceProperties = traceProperties;
    }

    @Override
    public void afterPropertiesSet() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            log.info("Archimedes: 当前日志实现不是 logback（{}），链路日志采集不可用，其余功能不受影响",
                    factory.getClass().getName());
            return;
        }
        LoggerContext context = (LoggerContext) factory;
        appender = new ArchimedesLogAppender(store, traceProperties.getMdcKey(), traceProperties.getSpanIdKey());
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        attachedContext = context;
        log.debug("Archimedes: 链路日志采集 Appender 已挂载 root logger");
    }

    @Override
    public void destroy() {
        if (attachedContext != null && appender != null) {
            attachedContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
    }
}
