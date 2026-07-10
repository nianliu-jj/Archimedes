package io.github.nianliu.archimedes.exampleall.trace;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.NoApiWrapper;
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
@ApiModule(name = "链路追踪演示", description = "四条路径演示 traceId 跨线程传递，用响应头 X-Trace-Id 回查链路日志")
// 这些端点返回裸 String 且不经统一包装（String 无法被 ResultVo 包裹）；
// 标 @NoApiWrapper 使契约 responseSchema 与真实返回一致（裸 String，不套 ResultVo）。
@NoApiWrapper
public class TraceDemoController {

    private static final Logger log = LoggerFactory.getLogger(TraceDemoController.class);

    private final AsyncWorker asyncWorker;
    private final ExecutorService bizPool;

    public TraceDemoController(AsyncWorker asyncWorker, ExecutorService bizPool) {
        this.asyncWorker = asyncWorker;
        this.bizPool = bizPool;
    }

    /** 基线：仅请求线程打日志 */
    @ApiDoc(summary = "同步基线", description = "仅请求线程打一条日志，作为 traceId 基线对照")
    @GetMapping("/sync")
    public String sync() {
        log.info("sync endpoint invoked");
        return MDC.get("traceId");
    }

    /** @Async 路径：请求线程 + all-async-* 线程各一条日志 */
    @ApiDoc(summary = "@Async 异步路径", description = "@Async 线程由 taskExecutor 自动装饰，日志与请求线程聚合在同一 traceId")
    @GetMapping("/async")
    public String asyncPath() {
        log.info("async endpoint invoked on request thread");
        asyncWorker.runAsync();
        return MDC.get("traceId");
    }

    /** 自定义池路径：bizPool 已被 Archimedes 自动包装，任务内 MDC 自动就位 */
    @ApiDoc(summary = "自定义线程池路径", description = "bizPool 被 BeanPostProcessor 自动包装，提交任务内 MDC 自动就位")
    @GetMapping("/pool")
    public String pool() {
        log.info("pool endpoint invoked on request thread");
        bizPool.submit(() -> log.info("task on bizPool, traceId={}", MDC.get("traceId")));
        return MDC.get("traceId");
    }

    /** 手动包装路径：commonPool 不归容器管，需 MdcWrappers 一行包装 */
    @ApiDoc(summary = "手动包装路径", description = "commonPool 不归容器管理，需 MdcWrappers.wrap 手动传递 MDC（自动化盲区示范）")
    @GetMapping("/manual")
    public String manual() {
        log.info("manual endpoint invoked on request thread");
        CompletableFuture.runAsync(MdcWrappers.wrap(
                (Runnable) () -> log.info("task on commonPool via MdcWrappers, traceId={}", MDC.get("traceId"))));
        return MDC.get("traceId");
    }
}
