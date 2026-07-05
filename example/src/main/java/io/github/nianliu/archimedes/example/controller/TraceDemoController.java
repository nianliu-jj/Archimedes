package io.github.nianliu.archimedes.example.controller;

import io.github.nianliu.archimedes.example.async.AsyncDemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trace")
public class TraceDemoController {

    private static final Logger log = LoggerFactory.getLogger(TraceDemoController.class);

    private final AsyncDemoService asyncDemoService;

    public TraceDemoController(AsyncDemoService asyncDemoService) {
        this.asyncDemoService = asyncDemoService;
    }

    @GetMapping("/current")
    public Map<String, String> current() {
        log.info("trace demo endpoint invoked");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("traceId", MDC.get("traceId"));
        body.put("spanId", MDC.get("spanId"));
        return body;
    }

    @GetMapping("/async")
    public Map<String, String> async() {
        log.info("async demo invoked on request thread");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("mainThreadTraceId", MDC.get("traceId"));
        body.put("asyncThreadTraceId", asyncDemoService.traceIdInAsyncThread().join());
        return body;
    }
}
