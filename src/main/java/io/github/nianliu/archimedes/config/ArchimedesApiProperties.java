package io.github.nianliu.archimedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "archimedes.api")
public class ArchimedesApiProperties {

    /** base-path 默认值；同时供 ArchimedesApiController 的 @GetMapping 占位符默认使用，避免多处硬编码。 */
    public static final String DEFAULT_BASE_PATH = "/archimedes";

    /** 总开关。 */
    private boolean enabled = true;

    /** 端点根路径；JSON = {basePath}/apis，UI = {basePath}。规范键为 kebab-case：archimedes.api.base-path。 */
    private String basePath = DEFAULT_BASE_PATH;

    /** 是否在 {basePath} 挂载内置页面。 */
    private boolean uiEnabled = true;

    /** 非空时只扫描这些包前缀下的 Controller。 */
    private List<String> basePackages = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public boolean isUiEnabled() {
        return uiEnabled;
    }

    public void setUiEnabled(boolean uiEnabled) {
        this.uiEnabled = uiEnabled;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }
}
