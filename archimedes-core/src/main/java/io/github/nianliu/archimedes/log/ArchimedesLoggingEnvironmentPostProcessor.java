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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class ArchimedesLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 本处理器注入的 PropertySource 名称（兼作幂等判定标记）。 */
    static final String PROPERTY_SOURCE_NAME = "archimedesLogging";
    /** 兜底 logback 配置在 classpath 中的位置。 */
    static final String FALLBACK_CONFIG = "classpath:archimedes-logback.xml";

    /** 宿主的 logback 约定配置文件名：命中任一即视为用户已自配、本处理器让位。 */
    private static final String[] USER_CONFIG_LOCATIONS = {
            "logback-test.xml", "logback-spring.xml", "logback.xml",
            "logback-spring.groovy", "logback.groovy"
    };

    /**
     * 在日志系统初始化前，按让位规则决定是否把 logging.config 指向内置兜底配置。
     * 任一让位条件命中即直接返回，最大限度不干扰宿主既有日志配置。
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 幂等：Spring Cloud bootstrap 等场景会多次调用，已注入则跳过
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        // 开关关闭时让位
        if (!"true".equalsIgnoreCase(environment.getProperty("archimedes.log.fallback-enabled", "true"))) {
            return;
        }
        // 用户已显式指定 logging.config 时让位
        if (environment.getProperty("logging.config") != null) {
            return;
        }
        // 日志实现非 logback 时兜底配置无意义，让位
        if (!ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", getClass().getClassLoader())) {
            return;
        }
        // 用户已存在 logback 约定配置文件时让位
        if (userLogbackConfigPresent()) {
            return;
        }
        // 以上均不命中：追加低优先级 PropertySource，把 logging.config 指向内置兜底配置
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Collections.<String, Object>singletonMap("logging.config", FALLBACK_CONFIG)));
    }

    /** 扫描 classpath 是否存在任一 logback 约定配置文件。 */
    private boolean userLogbackConfigPresent() {
        for (String location : USER_CONFIG_LOCATIONS) {
            if (new ClassPathResource(location).exists()) {
                return true;
            }
        }
        return false;
    }

    /** 置于较高优先级（HIGHEST+10），确保早于日志监听器（HIGHEST+20）完成注入。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
