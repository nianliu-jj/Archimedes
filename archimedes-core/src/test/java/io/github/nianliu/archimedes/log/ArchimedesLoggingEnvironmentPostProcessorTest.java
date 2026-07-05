package io.github.nianliu.archimedes.log;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesLoggingEnvironmentPostProcessorTest {

    private final ArchimedesLoggingEnvironmentPostProcessor processor =
            new ArchimedesLoggingEnvironmentPostProcessor();

    @Test
    void injectsFallbackWhenNoUserConfig() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("logging.config"))
                .isEqualTo(ArchimedesLoggingEnvironmentPostProcessor.FALLBACK_CONFIG);
    }

    @Test
    void skipsWhenLoggingConfigAlreadySet() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("logging.config", "classpath:user-logback.xml");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("logging.config")).isEqualTo("classpath:user-logback.xml");
        assertThat(env.getPropertySources()
                .contains(ArchimedesLoggingEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void skipsWhenFallbackDisabled() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("archimedes.log.fallback-enabled", "false");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("logging.config")).isNull();
    }

    @Test
    void injectionIsIdempotent() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, null);
        processor.postProcessEnvironment(env, null);

        long count = 0;
        for (org.springframework.core.env.PropertySource<?> ps : env.getPropertySources()) {
            if (ArchimedesLoggingEnvironmentPostProcessor.PROPERTY_SOURCE_NAME.equals(ps.getName())) {
                count++;
            }
        }
        assertThat(count).isEqualTo(1);
    }
}
