package io.github.nianliu.archimedes.log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.util.Collections;

/**
 * 日志系统初始化前的兜底注入：宿主没有任何 logback 配置时，把 logging.config 指向内置的
 * archimedes-logback.xml（pattern 含 traceId/spanId + 滚动文件输出）。
 *
 * <p>让位规则（任一命中即不介入）：logging.config 已设置；classpath 存在任一 logback
 * 约定配置文件；日志实现非 logback；archimedes.log.fallback-enabled=false。
 *
 * <p>时机：EnvironmentPostProcessorApplicationListener（HIGHEST+10）先于
 * LoggingApplicationListener（HIGHEST+20），注入在日志初始化之前完成。
 */
public class ArchimedesLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "archimedesLogging";
    static final String FALLBACK_CONFIG = "classpath:archimedes-logback.xml";

    private static final String[] USER_CONFIG_LOCATIONS = {
            "logback-test.xml", "logback-spring.xml", "logback.xml",
            "logback-spring.groovy", "logback.groovy"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return; // 幂等（Spring Cloud bootstrap 等场景会多次调用）
        }
        if (!"true".equalsIgnoreCase(environment.getProperty("archimedes.log.fallback-enabled", "true"))) {
            return;
        }
        if (environment.getProperty("logging.config") != null) {
            return;
        }
        if (!ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", getClass().getClassLoader())) {
            return;
        }
        if (userLogbackConfigPresent()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Collections.<String, Object>singletonMap("logging.config", FALLBACK_CONFIG)));
    }

    private boolean userLogbackConfigPresent() {
        for (String location : USER_CONFIG_LOCATIONS) {
            if (new ClassPathResource(location).exists()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
