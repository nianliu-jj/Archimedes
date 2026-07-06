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

    /** 安全认证方案：配置后 UI 调试面板自动携带认证信息（类似 Swagger 的 Authorize 功能）。 */
    private SecurityScheme security = new SecurityScheme();

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

    public SecurityScheme getSecurity() {
        return security;
    }

    public void setSecurity(SecurityScheme security) {
        this.security = security;
    }

    /**
     * 安全认证方案配置（参考 springdoc 中 Swagger 接入方式）。
     * <ul>
     *   <li>{@code type=BEARER}（默认）：UI 调试面板提供 Token 输入框，请求时自动携带
     *       {@code Authorization: Bearer {token}} 头；适配 Spring Security JWT、Sa-Token 等</li>
     *   <li>{@code type=BASIC}：提供用户名/密码输入，请求时携带 Basic auth 头</li>
     *   <li>{@code type=API_KEY}：提供 Key 输入框，请求时在指定头或 Query 参数携带</li>
     *   <li>{@code type=NONE}：关闭认证功能（UI 调试不携带认证信息）</li>
     * </ul>
     */
    public static class SecurityScheme {

        /** 认证类型（默认 NONE，即不携带认证信息）。 */
        private SecurityType type = SecurityType.NONE;

        /** Bearer/ApiKey 的头名称（默认 Authorization）。 */
        private String headerName = "Authorization";

        /** Bearer token 前缀（默认 "Bearer "）。 */
        private String bearerPrefix = "Bearer ";

        /** API Key 的传递位置（HEADER 或 QUERY，默认 HEADER）。 */
        private String apiKeyIn = "HEADER";

        /** API Key 的参数名（apiKeyIn=QUERY 时用作查询参数名）。 */
        private String apiKeyName = "api_key";

        /** Sa-Token 的 token 名称（默认 satoken，与 sa-token 配置一致）。 */
        private String saTokenName = "satoken";

        public SecurityType getType() {
            return type;
        }

        public void setType(SecurityType type) {
            this.type = type;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getBearerPrefix() {
            return bearerPrefix;
        }

        public void setBearerPrefix(String bearerPrefix) {
            this.bearerPrefix = bearerPrefix;
        }

        public String getApiKeyIn() {
            return apiKeyIn;
        }

        public void setApiKeyIn(String apiKeyIn) {
            this.apiKeyIn = apiKeyIn;
        }

        public String getApiKeyName() {
            return apiKeyName;
        }

        public void setApiKeyName(String apiKeyName) {
            this.apiKeyName = apiKeyName;
        }

        public String getSaTokenName() {
            return saTokenName;
        }

        public void setSaTokenName(String saTokenName) {
            this.saTokenName = saTokenName;
        }
    }

    /** 认证类型枚举。 */
    public enum SecurityType {
        /** 不携带认证信息。 */
        NONE,
        /** Bearer Token（JWT/Sa-Token 等）：Authorization: Bearer {token}。 */
        BEARER,
        /** HTTP Basic 认证。 */
        BASIC,
        /** API Key（请求头或 Query 参数）。 */
        API_KEY,
        /** Sa-Token 专用：在 Cookie/Header 中携带 satoken={token}。 */
        SA_TOKEN
    }
}
