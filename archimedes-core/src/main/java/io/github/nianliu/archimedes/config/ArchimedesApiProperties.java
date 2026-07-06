package io.github.nianliu.archimedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Archimedes 的配置属性绑定类，前缀为 {@code archimedes.api}。
 * <p>集中承载 starter 的所有可调开关（总开关、端点根路径、内置 UI 开关、扫描包过滤），
 * 由 Spring Boot 通过 {@link ConfigurationProperties} 自动从配置源绑定。
 * 设计要点：所有默认值内聚在本类，避免在 Controller/AutoConfiguration 中散落硬编码。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
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

    /** 热监听推送间隔（秒），0 表示实时推送（每次请求都重新扫描比对），默认 0。 */
    private int watchIntervalSeconds = 0;

    public boolean isEnabled() {
        return enabled;
    }

    /** 设置 starter 总开关。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    /** 设置端点根路径（建议保持以 {@code /} 开头，不以 {@code /} 结尾）。 */
    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public boolean isUiEnabled() {
        return uiEnabled;
    }

    /** 设置是否挂载内置 UI 页面。 */
    public void setUiEnabled(boolean uiEnabled) {
        this.uiEnabled = uiEnabled;
    }

    public List<String> getBasePackages() {
        return basePackages;
    }

    /** 设置扫描包前缀白名单；空列表表示不做包级过滤。 */
    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    public int getWatchIntervalSeconds() {
        return watchIntervalSeconds;
    }

    /** 设置热监听推送间隔（秒），0=实时（默认）；大于 0 时按该频率定时比对推送。 */
    public void setWatchIntervalSeconds(int watchIntervalSeconds) {
        this.watchIntervalSeconds = watchIntervalSeconds;
    }
}
