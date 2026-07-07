package io.github.nianliu.archimedes.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesRebinderTest {

    private final Map<String, Object> source = new HashMap<>();
    private AnnotationConfigApplicationContext context;

    private ConfigurationPropertiesRebinder rebinderFor(Class<?>... configs) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", source));
        context.register(configs);
        context.refresh();
        return new ConfigurationPropertiesRebinder(context);
    }

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void rebindsBeanWhosePrefixMatchesChangedKey() {
        source.put("demo.conf.title", "v1");
        ConfigurationPropertiesRebinder rebinder = rebinderFor(JavaBeanConfig.class);
        DemoConf bean = context.getBean(DemoConf.class);
        assertThat(bean.getTitle()).isEqualTo("v1");

        source.put("demo.conf.title", "v2");
        List<String> refreshed = rebinder.rebind(Set.of("demo.conf.title"));

        assertThat(bean.getTitle()).isEqualTo("v2");
        assertThat(refreshed).hasSize(1);
    }

    @Test
    void ignoresBeanWhenPrefixDoesNotMatch() {
        source.put("demo.conf.title", "v1");
        ConfigurationPropertiesRebinder rebinder = rebinderFor(JavaBeanConfig.class);
        DemoConf bean = context.getBean(DemoConf.class);

        source.put("demo.conf.title", "v2");
        List<String> refreshed = rebinder.rebind(Set.of("other.namespace.key"));

        // prefix 未命中：不重绑定，Bean 保持旧值
        assertThat(bean.getTitle()).isEqualTo("v1");
        assertThat(refreshed).isEmpty();
    }

    @Test
    void skipsImmutableBeanWithoutWritableProperties() {
        ConfigurationPropertiesRebinder rebinder = rebinderFor(ImmutableBeanConfig.class);

        List<String> refreshed = rebinder.rebind(Set.of("demo.fixed.name"));

        // 无 setter 的 Bean 防御式跳过，不抛异常
        assertThat(refreshed).isEmpty();
        assertThat(context.getBean(FixedConf.class).getName()).isEqualTo("origin");
    }

    @Test
    void emptyChangedKeysIsNoOp() {
        ConfigurationPropertiesRebinder rebinder = rebinderFor(JavaBeanConfig.class);
        assertThat(rebinder.rebind(Set.of())).isEmpty();
        assertThat(rebinder.rebind(null)).isEmpty();
    }

    @Configuration
    @EnableConfigurationProperties(DemoConf.class)
    static class JavaBeanConfig {
    }

    @ConfigurationProperties(prefix = "demo.conf")
    static class DemoConf {
        private String title = "default";

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @Configuration
    static class ImmutableBeanConfig {
        @Bean
        FixedConf fixedConf() {
            return new FixedConf("origin");
        }
    }

    /** 模拟构造器绑定的不可变属性类：只有 getter，无可写属性。 */
    @ConfigurationProperties(prefix = "demo.fixed")
    static class FixedConf {
        private final String name;

        FixedConf(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
