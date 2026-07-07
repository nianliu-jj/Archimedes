package io.github.nianliu.archimedes.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 监控的配置属性绑定类，前缀为 {@code archimedes.sql}。
 * <ul>
 *   <li>{@code enabled}：SQL 监控总开关（默认开启，关闭后数据源不包装、端点不装配）；</li>
 *   <li>{@code slow-sql-millis}：慢 SQL 阈值毫秒（耗时 &gt;= 阈值判为慢，默认 1000）；</li>
 *   <li>{@code max-history-size}：最近执行与慢 SQL 环形缓冲各自的条数上限（默认 500）；</li>
 *   <li>{@code capture-parameters}：是否采集绑定参数（默认开启；参数可能含敏感数据，可关）；</li>
 *   <li>{@code exclude-beans}：按 Bean 名排除不包装的数据源（逃生口）；</li>
 *   <li>{@code max-sql-stats}：去重 SQL 聚合条目上限，防止动态拼接 SQL 撑爆内存（默认 1000）。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@ConfigurationProperties(prefix = "archimedes.sql")
public class SqlMonitorProperties {

    /** SQL 监控总开关。 */
    private boolean enabled = true;

    /** 慢 SQL 阈值（毫秒），执行耗时 >= 该值判定为慢 SQL。 */
    private long slowSqlMillis = 1000;

    /** 最近执行与慢 SQL 两个环形缓冲各自的条数上限。 */
    private int maxHistorySize = 500;

    /** 是否采集 PreparedStatement 绑定参数。 */
    private boolean captureParameters = true;

    /** 按 Bean 名排除的数据源（不包装、不监控）。 */
    private List<String> excludeBeans = new ArrayList<>();

    /** 去重 SQL 聚合条目上限；达到后新 SQL 不再新建聚合槽（明细仍记录）。 */
    private int maxSqlStats = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    /** 设置 SQL 监控总开关。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSlowSqlMillis() {
        return slowSqlMillis;
    }

    /** 设置慢 SQL 阈值（毫秒）。 */
    public void setSlowSqlMillis(long slowSqlMillis) {
        this.slowSqlMillis = slowSqlMillis;
    }

    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    /** 设置最近执行/慢 SQL 环形缓冲上限。 */
    public void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public boolean isCaptureParameters() {
        return captureParameters;
    }

    /** 设置是否采集绑定参数。 */
    public void setCaptureParameters(boolean captureParameters) {
        this.captureParameters = captureParameters;
    }

    public List<String> getExcludeBeans() {
        return excludeBeans;
    }

    /** 设置排除包装的数据源 Bean 名列表。 */
    public void setExcludeBeans(List<String> excludeBeans) {
        this.excludeBeans = excludeBeans;
    }

    public int getMaxSqlStats() {
        return maxSqlStats;
    }

    /** 设置去重 SQL 聚合条目上限。 */
    public void setMaxSqlStats(int maxSqlStats) {
        this.maxSqlStats = maxSqlStats;
    }
}
