package io.github.nianliu.archimedes.env;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentConfigServiceTest {

    private final StandardEnvironment environment = new StandardEnvironment();
    private final ConfigManagementProperties properties = new ConfigManagementProperties();
    private final EnvironmentConfigService service = new EnvironmentConfigService(environment, properties);

    @Test
    void listsEntriesGroupedBySourceInPrecedenceOrder() {
        environment.getPropertySources().addLast(new MapPropertySource("low", Map.of("app.other", "x")));
        environment.getPropertySources().addFirst(new MapPropertySource("high", Map.of("app.name", "arch")));

        List<ConfigSnapshot.PropertySourceView> views = service.listPropertySources();

        List<String> names = views.stream().map(ConfigSnapshot.PropertySourceView::getName).toList();
        // addFirst 的源优先级最高，应排在最前；addLast 的源在最后
        assertThat(names.indexOf("high")).isZero();
        assertThat(names.indexOf("low")).isEqualTo(names.size() - 1);

        ConfigSnapshot.PropertySourceView high = views.get(0);
        assertThat(high.getEntries())
                .anySatisfy(e -> {
                    assertThat(e.getKey()).isEqualTo("app.name");
                    assertThat(e.getValue()).isEqualTo("arch");
                    assertThat(e.isSensitive()).isFalse();
                });
    }

    @Test
    void masksSensitiveKeysByDefault() {
        environment.getPropertySources().addFirst(new MapPropertySource("src", Map.of(
                "spring.datasource.password", "123456",
                "my.api-token", "t-1",
                "jwt.secret", "s-1",
                "app.title", "plain")));

        List<ConfigSnapshot.ConfigEntry> entries = service.listPropertySources().get(0).getEntries();

        assertThat(valueOf(entries, "spring.datasource.password")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(valueOf(entries, "my.api-token")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(valueOf(entries, "jwt.secret")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(valueOf(entries, "app.title")).isEqualTo("plain");
    }

    @Test
    void customSensitiveKeysReplaceDefaults() {
        properties.setSensitiveKeys(List.of("internal"));
        environment.getPropertySources().addFirst(new MapPropertySource("src", Map.of(
                "app.internal.endpoint", "http://in",
                "spring.datasource.password", "123456")));

        List<ConfigSnapshot.ConfigEntry> entries = service.listPropertySources().get(0).getEntries();

        // 自定义关键字生效，默认关键字（password）不再触发
        assertThat(valueOf(entries, "app.internal.endpoint")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(valueOf(entries, "spring.datasource.password")).isEqualTo("123456");
    }

    @Test
    void skipsNonEnumerableSources() {
        environment.getPropertySources().addFirst(new PropertySource<Object>("opaque", new Object()) {
            @Override
            public Object getProperty(String name) {
                return null;
            }
        });

        List<String> names = service.listPropertySources().stream()
                .map(ConfigSnapshot.PropertySourceView::getName).toList();

        assertThat(names).doesNotContain("opaque");
    }

    @Test
    void skipsOnlyTheFailingKey() {
        environment.getPropertySources().addFirst(new EnumerablePropertySource<Object>("flaky") {
            @Override
            public String[] getPropertyNames() {
                return new String[]{"good.key", "bad.key"};
            }

            @Override
            public Object getProperty(String name) {
                if ("bad.key".equals(name)) {
                    throw new IllegalStateException("boom");
                }
                return "ok";
            }
        });

        ConfigSnapshot.PropertySourceView view = service.listPropertySources().get(0);

        assertThat(view.getName()).isEqualTo("flaky");
        assertThat(view.getEntries()).hasSize(1);
        assertThat(view.getEntries().get(0).getKey()).isEqualTo("good.key");
    }

    @Test
    void maskIfSensitiveFollowsSameRule() {
        assertThat(service.maskIfSensitive("db.password", "x")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(service.maskIfSensitive("app.title", "x")).isEqualTo("x");
        assertThat(service.maskIfSensitive("db.password", null)).isNull();
    }

    private String valueOf(List<ConfigSnapshot.ConfigEntry> entries, String key) {
        return entries.stream().filter(e -> e.getKey().equals(key))
                .map(ConfigSnapshot.ConfigEntry::getValue).findFirst().orElse(null);
    }
}
