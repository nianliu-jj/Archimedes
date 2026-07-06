package io.github.nianliu.archimedes.exampleall.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @Async 工作者：方法运行在 taskExecutor 线程上，
 * 打出的日志应携带发起请求的 traceId（自动传递验证点）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Service
public class AsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncWorker.class);

    /** 异步任务：日志的 MDC traceId 应与请求线程一致 */
    @Async
    public void runAsync() {
        log.info("async task on @Async pool, traceId={}", MDC.get("traceId"));
    }
}
