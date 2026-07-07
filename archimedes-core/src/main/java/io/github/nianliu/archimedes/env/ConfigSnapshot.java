package io.github.nianliu.archimedes.env;

import java.util.List;

/**
 * 配置查询端点的响应模型：热更新开关状态 + 动态覆盖键列表 + 按优先级排序的属性源视图。
 * <p>字段结构即 JSON 输出契约，前端「配置中心」Tab 据此渲染。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class ConfigSnapshot {

    /** 热更新开关当前状态（false 时前端禁用编辑控件）。 */
    private final boolean hotRefreshEnabled;
    /** 当前存在动态覆盖的配置键（前端据此渲染覆盖标识与移除按钮）。 */
    private final List<String> dynamicKeys;
    /** 全部可枚举属性源（按 Environment 优先级顺序，越靠前优先级越高）。 */
    private final List<PropertySourceView> propertySources;

    public ConfigSnapshot(boolean hotRefreshEnabled, List<String> dynamicKeys,
                          List<PropertySourceView> propertySources) {
        this.hotRefreshEnabled = hotRefreshEnabled;
        this.dynamicKeys = dynamicKeys;
        this.propertySources = propertySources;
    }

    public boolean isHotRefreshEnabled() {
        return hotRefreshEnabled;
    }

    public List<String> getDynamicKeys() {
        return dynamicKeys;
    }

    public List<PropertySourceView> getPropertySources() {
        return propertySources;
    }

    /** 单个属性源视图：来源名称 + 其下全部配置项。 */
    public static class PropertySourceView {

        /** 属性源名称（即配置项的"来源"标注，如 applicationConfig: [classpath:/application.properties]）。 */
        private final String name;
        /** 该属性源下的全部配置项。 */
        private final List<ConfigEntry> entries;

        public PropertySourceView(String name, List<ConfigEntry> entries) {
            this.name = name;
            this.entries = entries;
        }

        public String getName() {
            return name;
        }

        public List<ConfigEntry> getEntries() {
            return entries;
        }
    }

    /** 单个配置项：key / value（敏感项已脱敏）/ 敏感标记。 */
    public static class ConfigEntry {

        private final String key;
        /** 展示值；敏感项固定为掩码 {@code ******}。 */
        private final String value;
        /** 是否命中敏感关键字（前端可据此提示）。 */
        private final boolean sensitive;

        public ConfigEntry(String key, String value, boolean sensitive) {
            this.key = key;
            this.value = value;
            this.sensitive = sensitive;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public boolean isSensitive() {
            return sensitive;
        }
    }
}
