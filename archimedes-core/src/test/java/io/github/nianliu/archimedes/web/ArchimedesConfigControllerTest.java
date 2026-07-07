package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.env.ConfigManagementProperties;
import io.github.nianliu.archimedes.env.ConfigSnapshot;
import io.github.nianliu.archimedes.env.ConfigurationPropertiesRebinder;
import io.github.nianliu.archimedes.env.DynamicConfigManager;
import io.github.nianliu.archimedes.env.EnvironmentConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesConfigControllerTest {

    private final StandardEnvironment environment = new StandardEnvironment();
    private final ConfigManagementProperties properties = new ConfigManagementProperties();
    private AnnotationConfigApplicationContext context;
    private ArchimedesConfigController controller;

    @BeforeEach
    void setUp() {
        Map<String, Object> base = new HashMap<>();
        base.put("app.greeting", "hello");
        base.put("my.password", "boom");
        environment.getPropertySources().addLast(new MapPropertySource("base", base));

        context = new AnnotationConfigApplicationContext();
        context.register(EmptyConfig.class);
        context.refresh();

        EnvironmentConfigService service = new EnvironmentConfigService(environment, properties);
        DynamicConfigManager manager = new DynamicConfigManager(environment,
                new ConfigurationPropertiesRebinder(context), event -> { });
        controller = new ArchimedesConfigController(service, manager, properties);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void configReturnsSnapshotWithMaskedSensitiveValues() {
        ConfigSnapshot snapshot = controller.config();

        assertThat(snapshot.isHotRefreshEnabled()).isTrue();
        assertThat(snapshot.getDynamicKeys()).isEmpty();
        ConfigSnapshot.ConfigEntry password = findEntry(snapshot, "my.password");
        assertThat(password.getValue()).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(password.isSensitive()).isTrue();
        assertThat(findEntry(snapshot, "app.greeting").getValue()).isEqualTo("hello");
    }

    @Test
    void updateRejectsMissingKeyWith400() {
        assertThat(controller.update(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.update(Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.update(Map.of("key", "  ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsWith403WhenHotRefreshDisabled() {
        properties.setHotRefreshEnabled(false);

        ResponseEntity<Map<String, Object>> resp =
                controller.update(Map.of("key", "app.greeting", "value", "hi"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 关闭状态下不得产生任何变更
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hello");
        assertThat(controller.config().getDynamicKeys()).isEmpty();
    }

    @Test
    void updateAppliesOverrideAndReportsDetail() {
        ResponseEntity<Map<String, Object>> resp =
                controller.update(Map.of("key", "app.greeting", "value", "hi"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("oldValue")).isEqualTo("hello");
        assertThat(resp.getBody().get("newValue")).isEqualTo("hi");
        assertThat(resp.getBody().get("removed")).isEqualTo(false);
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hi");
        assertThat(controller.config().getDynamicKeys()).contains("app.greeting");
    }

    @Test
    void updateMasksSensitiveValuesInResponse() {
        ResponseEntity<Map<String, Object>> resp =
                controller.update(Map.of("key", "my.password", "value", "new-secret"));

        // 敏感 key 的新旧值都不得明文回显
        assertThat(resp.getBody().get("oldValue")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(resp.getBody().get("newValue")).isEqualTo(EnvironmentConfigService.MASK);
        assertThat(environment.getProperty("my.password")).isEqualTo("new-secret");
    }

    @Test
    void updateWithoutValueRemovesOverride() {
        controller.update(Map.of("key", "app.greeting", "value", "hi"));

        Map<String, String> removal = new HashMap<>();
        removal.put("key", "app.greeting");
        ResponseEntity<Map<String, Object>> resp = controller.update(removal);

        assertThat(resp.getBody().get("removed")).isEqualTo(true);
        assertThat(environment.getProperty("app.greeting")).isEqualTo("hello");
    }

    private ConfigSnapshot.ConfigEntry findEntry(ConfigSnapshot snapshot, String key) {
        List<ConfigSnapshot.PropertySourceView> sources = snapshot.getPropertySources();
        return sources.stream()
                .flatMap(s -> s.getEntries().stream())
                .filter(e -> e.getKey().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Configuration
    static class EmptyConfig {
    }
}
