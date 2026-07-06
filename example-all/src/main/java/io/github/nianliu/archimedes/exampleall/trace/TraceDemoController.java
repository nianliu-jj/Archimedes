package io.github.nianliu.archimedes.exampleall.trace;

import io.github.nianliu.archimedes.exampleall.trace.AsyncWorker;
import io.github.nianliu.archimedes.trace.propagation.MdcWrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 链路追踪测试面：四条路径各打一条日志，随后用响应头 X-Trace-Id 到
 * {@code /archimedes/logs/trace/{traceId}}（或 UI Trace Logs Tab）查询，
 * 应看到请求线程 + 各异步线程的日志聚合在同一 traceId 下。
 *
 * <ul>
 *   <li>/trace/sync    —— 请求线程日志（基线）</li>
 *   <li>/trace/async   —— @Async 线程（taskExecutor 自动装饰）</li>
 *   <li>/trace/pool    —— 自定义 ExecutorService Bean（BPP 自动包装）</li>
 *   <li>/trace/manual  —— CompletableFuture commonPool（MdcWrappers 手动包装，自动化盲区示范）</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@RestController
@RequestMapping("/trace")
public class TraceDemoController {

    private static final Logger log = LoggerFactory.getLogger(TraceDemoController.class);

    private final AsyncWorker asyncWorker;
    private final ExecutorService bizPool;

    public TraceDemoController(AsyncWorker asyncWorker, ExecutorService bizPool) {
        this.asyncWorker = asyncWorker;
        this.bizPool = bizPool;
    }

    /** 基线：仅请求线程打日志 */
    @GetMapping("/sync")
    public String sync() {
        log.info("sync endpoint invoked");
        return MDC.get("traceId");
    }

    /** @Async 路径：请求线程 + all-async-* 线程各一条日志 */
    @GetMapping("/async")
    public String asyncPath() {
        log.info("async endpoint invoked on request thread");
        asyncWorker.runAsync();
        return MDC.get("traceId");
    }

    /** 自定义池路径：bizPool 已被 Archimedes 自动包装，任务内 MDC 自动就位 */
    @GetMapping("/pool")
    public String pool() {
        log.info("pool endpoint invoked on request thread");
        bizPool.submit(() -> log.info("task on bizPool, traceId={}", MDC.get("traceId")));
        return MDC.get("traceId");
    }

    /** 手动包装路径：commonPool 不归容器管，需 MdcWrappers 一行包装 */
    @GetMapping("/manual")
    public String manual() {
        log.info("manual endpoint invoked on request thread");
        CompletableFuture.runAsync(MdcWrappers.wrap(
                (Runnable) () -> log.info("task on commonPool via MdcWrappers, traceId={}", MDC.get("traceId"))));
        return MDC.get("traceId");
    }
}
