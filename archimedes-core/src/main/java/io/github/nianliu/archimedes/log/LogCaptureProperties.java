package io.github.nianliu.archimedes.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archimedes.log.capture")
public class LogCaptureProperties {

    /** 采集总开关；false 时不注册采集与查询相关 Bean。 */
    private boolean enabled = true;

    /** 全局最大缓存条数，超限按最老 trace 整体淘汰。 */
    private int maxEntries = 10000;

    /** 单条链路最大缓存条数，超限丢弃该链路最老条目。 */
    private int maxEntriesPerTrace = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public int getMaxEntriesPerTrace() {
        return maxEntriesPerTrace;
    }

    public void setMaxEntriesPerTrace(int maxEntriesPerTrace) {
        this.maxEntriesPerTrace = maxEntriesPerTrace;
    }
}
