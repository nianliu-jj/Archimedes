package io.github.nianliu.archimedes.env;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置中心的配置属性绑定类，前缀为 {@code archimedes.config}。
 * <p>承载配置展示与热更新能力的全部开关：
 * <ul>
 *   <li>{@code enabled}：配置中心总开关（默认开启，关闭后端点与相关 Bean 均不装配）；</li>
 *   <li>{@code hot-refresh-enabled}：热更新开关（默认开启，关闭后 update 端点拒绝写入但查询仍可用）；</li>
 *   <li>{@code sensitive-keys}：敏感键关键字列表（key 小写化后 contains 命中即脱敏，
 *       用户配置该项会整体替换默认集合）。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@ConfigurationProperties(prefix = "archimedes.config")
public class ConfigManagementProperties {

    /** 默认敏感关键字：对齐 Spring Boot Sanitizer 的宽松取向，宁可误脱敏不可漏。 */
    private static final List<String> DEFAULT_SENSITIVE_KEYS =
            Arrays.asList("password", "secret", "token", "credential", "key");

    /** 配置中心总开关。 */
    private boolean enabled = true;

    /** 热更新开关；关闭后 POST update 端点返回 403，查询端点不受影响。 */
    private boolean hotRefreshEnabled = true;

    /** 敏感键关键字（contains 匹配，大小写不敏感）；用户配置后整体替换默认值。 */
    private List<String> sensitiveKeys = new ArrayList<>(DEFAULT_SENSITIVE_KEYS);

    public boolean isEnabled() {
        return enabled;
    }

    /** 设置配置中心总开关。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHotRefreshEnabled() {
        return hotRefreshEnabled;
    }

    /** 设置热更新开关（false = 只读模式）。 */
    public void setHotRefreshEnabled(boolean hotRefreshEnabled) {
        this.hotRefreshEnabled = hotRefreshEnabled;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    /** 设置敏感键关键字列表（整体替换默认集合）。 */
    public void setSensitiveKeys(List<String> sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys;
    }
}
