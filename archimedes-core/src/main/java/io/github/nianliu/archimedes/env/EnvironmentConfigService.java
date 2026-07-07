package io.github.nianliu.archimedes.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 配置枚举服务：遍历 {@link ConfigurableEnvironment} 的全部可枚举属性源，
 * 生成"来源 → 配置项"视图，并按敏感规则脱敏。
 * <p>设计要点：
 * <ul>
 *   <li>只读——本类不修改 Environment，写入职责在 {@link DynamicConfigManager}；</li>
 *   <li>防御式——非枚举型属性源跳过、单键取值异常跳过，任何单点失败不中断整表输出；</li>
 *   <li>实时快照——每次调用现场枚举，不做缓存（systemProperties 等属性源内容可变）。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class EnvironmentConfigService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfigService.class);

    /** 敏感值统一掩码。 */
    public static final String MASK = "******";

    private final ConfigurableEnvironment environment;
    private final ConfigManagementProperties properties;

    public EnvironmentConfigService(ConfigurableEnvironment environment,
                                    ConfigManagementProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    /**
     * 枚举全部可枚举属性源：迭代顺序即 Environment 优先级顺序（越靠前优先级越高），
     * 前端据此可判断同名 key 的实际生效来源。
     */
    public List<ConfigSnapshot.PropertySourceView> listPropertySources() {
        List<ConfigSnapshot.PropertySourceView> views = new ArrayList<>();
        int totalEntries = 0;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource)) {
                // 非枚举型（如 servletContext 初始化参数包装）无法列出键集合，与 actuator env 端点同等跳过
                log.debug("跳过非枚举型属性源: {}", source.getName());
                continue;
            }
            EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
            String[] names;
            try {
                names = enumerable.getPropertyNames();
            } catch (RuntimeException ex) {
                log.warn("属性源 {} 枚举键失败，整源跳过: {}", source.getName(), ex.getMessage());
                continue;
            }
            List<ConfigSnapshot.ConfigEntry> entries = new ArrayList<>(names.length);
            for (String key : names) {
                try {
                    Object raw = enumerable.getProperty(key);
                    boolean sensitive = isSensitive(key);
                    String value = sensitive ? MASK : (raw == null ? null : String.valueOf(raw));
                    entries.add(new ConfigSnapshot.ConfigEntry(key, value, sensitive));
                } catch (RuntimeException ex) {
                    // 单键取值异常（如属性源实现缺陷）只跳过该键，不影响其余输出
                    log.warn("属性源 {} 中键 {} 取值失败，跳过: {}", source.getName(), key, ex.getMessage());
                }
            }
            views.add(new ConfigSnapshot.PropertySourceView(source.getName(), entries));
            totalEntries += entries.size();
        }
        log.debug("配置枚举完成: {} 个属性源 / {} 个配置项", views.size(), totalEntries);
        return views;
    }

    /** 敏感键判定：key 小写化后 contains 任一关键字即命中（关键字集合见 archimedes.config.sensitive-keys）。 */
    public boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        List<String> keywords = properties.getSensitiveKeys();
        if (keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty()
                    && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 按敏感规则脱敏：敏感键返回掩码（null 值保持 null），非敏感键原样返回。 */
    public String maskIfSensitive(String key, String value) {
        if (value == null) {
            return null;
        }
        return isSensitive(key) ? MASK : value;
    }
}
