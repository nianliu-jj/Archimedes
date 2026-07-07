package io.github.nianliu.archimedes.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicConfigManagerTest {

    private final StandardEnvironment environment = new StandardEnvironment();
    private final List<Object> publishedEvents = new ArrayList<>();
    private AnnotationConfigApplicationContext context;
    private DynamicConfigManager manager;

    @BeforeEach
    void setUp() {
        Map<String, Object> base = new HashMap<>();
        base.put("app.greeting", "hello");
        environment.getPropertySources().addLast(new MapPropertySource("base", base));

        context = new AnnotationConfigApplicationContext();
        context.register(EmptyConfig.class);
        context.refresh();

        ApplicationEventPublisher publisher = publishedEvents::add;
        manager = new DynamicConfigManager(environment,
                new ConfigurationPropertiesRebinder(context), publisher);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void updateOverridesValueAtHighestPrecedence() {
        ConfigUpdateResult result = manager.update("app.greeting", "hi");

        assertThat(environment.getProperty("app.greeting")).isEqualTo("hi");
        assertThat(result.getOldValue()).isEqualTo("hello");
        assertThat(result.getNewValue()).isEqualTo("hi");
        assertThat(result.isRemoved()).isFalse();
        assertThat(manager.dynamicKeys()).containsExactly("app.greeting");
        // 动态属性源必须位于最高优先级（第一个）
        assertThat(environment.getPropertySources().iterator().next().getName())
                .isEqualTo(DynamicConfigManager.DYNAMIC_SOURCE_NAME);
    }

    @Test
    void removeRestoresUnderlyingValue() {
        manager.update("app.greeting", "hi");

        ConfigUpdateResult result = manager.update("app.greeting", null);

        assertThat(result.isRemoved()).isTrue();
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hello");
        assertThat(manager.dynamicKeys()).isEmpty();
    }

    @Test
    void updateBrandNewKeyHasNoOldValue() {
        ConfigUpdateResult result = manager.update("brand.new.key", "x");

        assertThat(result.getOldValue()).isNull();
        assertThat(result.getNewValue()).isEqualTo("x");
        assertThat(environment.getProperty("brand.new.key")).isEqualTo("x");
    }

    @Test
    void publishesArchimedesEventAndCloudEventReflectively() {
        manager.update("app.greeting", "hi");

        // 自有事件必发
        assertThat(publishedEvents).anySatisfy(e -> {
            assertThat(e).isInstanceOf(ArchimedesConfigChangedEvent.class);
            assertThat(((ArchimedesConfigChangedEvent) e).getKeys()).containsExactly("app.greeting");
        });
        // 测试 classpath 存在同 FQCN 桩类 → 反射发布路径生效
        assertThat(publishedEvents).anySatisfy(e -> {
            assertThat(e).isInstanceOf(EnvironmentChangeEvent.class);
            assertThat(((EnvironmentChangeEvent) e).getKeys()).containsExactly("app.greeting");
        });
    }

    @Test
    void dynamicKeysEmptyBeforeAnyUpdate() {
        assertThat(manager.dynamicKeys()).isEmpty();
    }

    @Configuration
    static class EmptyConfig {
    }
}
