package io.github.nianliu.archimedes.env;

import java.util.List;

/**
 * 单次配置热更新的变更明细：新旧生效值对比 + 是否为删除操作 + 被重绑定的 Bean 名列表。
 * <p>注意：{@code oldValue}/{@code newValue} 为 Environment 视角的<b>生效值</b>
 * （动态覆盖叠加底层配置源后的结果），控制器返回前会按敏感规则脱敏。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class ConfigUpdateResult {

    private final String key;
    /** 更新前的生效值（可能来自底层配置源或上一次动态覆盖）。 */
    private final String oldValue;
    /** 更新后的生效值（删除覆盖时即底层原值）。 */
    private final String newValue;
    /** true = 本次为删除动态覆盖（恢复原值）。 */
    private final boolean removed;
    /** 因本次变更被原地重绑定的 @ConfigurationProperties Bean 名称。 */
    private final List<String> refreshedBeans;

    public ConfigUpdateResult(String key, String oldValue, String newValue,
                              boolean removed, List<String> refreshedBeans) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.removed = removed;
        this.refreshedBeans = refreshedBeans;
    }

    public String getKey() {
        return key;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public boolean isRemoved() {
        return removed;
    }

    public List<String> getRefreshedBeans() {
        return refreshedBeans;
    }
}
