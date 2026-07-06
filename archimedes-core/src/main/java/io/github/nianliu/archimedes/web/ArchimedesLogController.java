package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.log.LogQueryResult;
import io.github.nianliu.archimedes.log.LogStore;
import io.github.nianliu.archimedes.trace.TraceProperties;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链路日志查询端点：按 traceId 分页查询该链路上全部日志（含跨线程），
 * 以及返回当前请求的 traceId。
 * <p>路径位于 {@code {base-path}/logs/trace/...} 与 {@code {base-path}/trace/current}，
 * 天然被 REST 扫描的 base-path 排除规则覆盖，不会出现在契约结果中。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
@RestController
public class ArchimedesLogController {

    private final LogStore logStore;
    private final TraceProperties traceProperties;

    public ArchimedesLogController(LogStore logStore, TraceProperties traceProperties) {
        this.logStore = logStore;
        this.traceProperties = traceProperties;
    }

    /** 按 traceId 查询链路日志：时间升序分页返回，含线程/级别/logger/spanId。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/logs/trace/{traceId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LogQueryResult queryByTraceId(@PathVariable String traceId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "200") int size) {
        return logStore.queryByTraceId(traceId, page, size);
    }

    /** 返回当前请求的 traceId（前端可用此端点获取 traceId 做联动查询）。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/trace/current",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> currentTraceId() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("traceId", MDC.get(traceProperties.getMdcKey()));
        return body;
    }
}
