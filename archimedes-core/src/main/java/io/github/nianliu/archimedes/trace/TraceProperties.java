package io.github.nianliu.archimedes.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /** 跨线程传递配置。 */
    private final Propagation propagation = new Propagation();

    public Propagation getPropagation() {
        return propagation;
    }

    public static class Propagation {

        /** 跨线程自动传递开关；false 时不包装任何 Executor Bean。 */
        private boolean enabled = true;

        /** 按 Bean 名排除，不参与自动包装（注入具体实现类的宿主代码的逃生口）。 */
        private List<String> excludeBeans = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludeBeans() {
            return excludeBeans;
        }

        public void setExcludeBeans(List<String> excludeBeans) {
            this.excludeBeans = excludeBeans;
        }
    }

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
