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

/** 链路日志查询端点（自身路径天然被 REST 扫描的 base-path 排除规则覆盖）。 */
@RestController
public class ArchimedesLogController {

    private final LogStore logStore;
    private final TraceProperties traceProperties;

    public ArchimedesLogController(LogStore logStore, TraceProperties traceProperties) {
        this.logStore = logStore;
        this.traceProperties = traceProperties;
    }

    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/logs/trace/{traceId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LogQueryResult queryByTraceId(@PathVariable String traceId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "200") int size) {
        return logStore.queryByTraceId(traceId, page, size);
    }

    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/trace/current",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> currentTraceId() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("traceId", MDC.get(traceProperties.getMdcKey()));
        return body;
    }
}
