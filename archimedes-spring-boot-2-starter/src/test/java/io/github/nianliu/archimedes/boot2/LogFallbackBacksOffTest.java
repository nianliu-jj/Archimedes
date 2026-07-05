package io.github.nianliu.archimedes.boot2;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本模块 test resources 放了用户自有 logback-spring.xml → 让位分支：
 * Archimedes 不注入 logging.config，宿主配置在用（USER_CONSOLE appender）。
 */
@SpringBootTest(classes = LogFallbackBacksOffTest.TestApp.class)
class LogFallbackBacksOffTest {

    @Autowired
    private Environment environment;

    @Test
    void doesNotInjectWhenUserConfigPresent() {
        assertThat(environment.getProperty("logging.config")).isNull();

        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.getAppender("USER_CONSOLE")).isNotNull();
        assertThat(root.getAppender("ARCHIMEDES_CONSOLE")).isNull();
        assertThat(root.getAppender("ARCHIMEDES_FILE")).isNull();
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
