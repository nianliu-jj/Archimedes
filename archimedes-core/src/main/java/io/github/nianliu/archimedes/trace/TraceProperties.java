package io.github.nianliu.archimedes.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archimedes.trace")
public class TraceProperties {

    /** trace 总开关；false 时不注册任何 trace 相关 Bean 与 Filter。 */
    private boolean enabled = true;

    /** 信任宿主自有 traceId：true 且请求进入时宿主已在 MDC 写入 traceId，则不覆盖、不清理。 */
    private boolean useProjectTraceId = false;

    /** 透传/回写 traceId 的 HTTP 头名称。 */
    private String headerName = "X-Trace-Id";

    /** 是否在响应头回写 traceId。 */
    private boolean responseHeader = true;

    /** traceId 在 MDC 中的 key。 */
    private String mdcKey = "traceId";

    /** spanId 在 MDC 中的 key。 */
    private String spanIdKey = "spanId";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isUseProjectTraceId() {
        return useProjectTraceId;
    }

    public void setUseProjectTraceId(boolean useProjectTraceId) {
        this.useProjectTraceId = useProjectTraceId;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(boolean responseHeader) {
        this.responseHeader = responseHeader;
    }

    public String getMdcKey() {
        return mdcKey;
    }

    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    public String getSpanIdKey() {
        return spanIdKey;
    }

    public void setSpanIdKey(String spanIdKey) {
        this.spanIdKey = spanIdKey;
    }
}
