package io.github.nianliu.archimedes.boot3;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本模块 test resources 无任何用户 logback 配置 → 兜底注入分支：
 * logging.config 被注入，root logger 挂 ARCHIMEDES 双 appender，pattern 含 traceId。
 */
@SpringBootTest(classes = LogFallbackEndToEndTest.TestApp.class)
class LogFallbackEndToEndTest {

    @Autowired
    private Environment environment;

    @Test
    void fallbackConfigInjectedAndApplied() {
        assertThat(environment.getProperty("logging.config"))
                .isEqualTo("classpath:archimedes-logback.xml");

        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(root.getAppender("ARCHIMEDES_CONSOLE")).isNotNull();
        assertThat(root.getAppender("ARCHIMEDES_FILE")).isNotNull();

        OutputStreamAppender<?> console =
                (OutputStreamAppender<?>) root.getAppender("ARCHIMEDES_CONSOLE");
        String pattern = ((PatternLayoutEncoder) console.getEncoder()).getPattern();
        assertThat(pattern).contains("%X{traceId").contains("%X{spanId");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
