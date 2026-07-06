package io.github.nianliu.archimedes.trace.propagation;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * MDC 跨线程传递的唯一公开入口。语义：包装时快照提交线程的 MDC，
 * 子线程执行前恢复快照、执行后还原子线程原有 MDC（池化线程复用安全）。
 *
 * <p>Spring 容器内的 Executor Bean 已由 MdcExecutorBeanPostProcessor 自动接入；
 * 本工具用于自动化盲区：CompletableFuture 默认 commonPool、宿主自建裸线程池等，例如
 * {@code CompletableFuture.supplyAsync(MdcWrappers.wrap(() -> ...))}。
 *
 * <p>设计要点：所有 wrap 重载共享同一套「快照—执行前恢复—执行后还原」模式。
 * 执行后必须还原子线程原有 MDC（而非简单清空），因为线程池会复用线程，
 * 若不还原会把本任务的 MDC 泄漏给下一个复用该线程的任务，造成 traceId 串号。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public final class MdcWrappers {

    /** 工具类，禁止实例化。 */
    private MdcWrappers() {
    }

    /**
     * 包装 Runnable，使其在执行时携带提交线程的 MDC 快照。
     *
     * @param task 原始任务
     * @return 携带 MDC 快照的任务
     */
    public static Runnable wrap(final Runnable task) {
        // 在包装的当下（即提交线程）抓取 MDC 快照，闭包捕获后带到子线程
        final Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return new Runnable() {
            @Override
            public void run() {
                // 备份子线程当前 MDC，执行结束后还原，保证池化线程复用安全
                Map<String, String> backup = MDC.getCopyOfContextMap();
                apply(snapshot);
                try {
                    task.run();
                } finally {
                    apply(backup);
                }
            }
        };
    }

    /**
     * 包装 Callable，使其在执行时携带提交线程的 MDC 快照。
     *
     * @param task 原始任务
     * @param <T>  返回值类型
     * @return 携带 MDC 快照的任务
     */
    public static <T> Callable<T> wrap(final Callable<T> task) {
        final Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                Map<String, String> backup = MDC.getCopyOfContextMap();
                apply(snapshot);
                try {
                    return task.call();
                } finally {
                    apply(backup);
                }
            }
        };
    }

    /**
     * 包装 Supplier，使其在执行时携带提交线程的 MDC 快照。
     * 主要服务于 {@code CompletableFuture.supplyAsync} 等场景。
     *
     * @param supplier 原始供给函数
     * @param <T>      返回值类型
     * @return 携带 MDC 快照的供给函数
     */
    public static <T> Supplier<T> wrap(final Supplier<T> supplier) {
        final Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return new Supplier<T>() {
            @Override
            public T get() {
                Map<String, String> backup = MDC.getCopyOfContextMap();
                apply(snapshot);
                try {
                    return supplier.get();
                } finally {
                    apply(backup);
                }
            }
        };
    }

    /**
     * 包装 Executor：其提交的每个任务都会自动携带 MDC 快照。
     *
     * @param executor 原始 Executor
     * @return 委托包装器；若已是包装器则原样返回，避免重复包装
     */
    public static Executor wrap(Executor executor) {
        // 幂等：已是包装器则直接返回，防止 BeanPostProcessor 与手动调用叠加导致多层包装
        if (executor instanceof MdcExecutor) {
            return executor;
        }
        return new MdcExecutor(executor);
    }

    /**
     * 包装 ExecutorService：其所有提交方法都会自动携带 MDC 快照。
     *
     * @param executorService 原始 ExecutorService
     * @return 委托包装器；若已是包装器则原样返回
     */
    public static ExecutorService wrap(ExecutorService executorService) {
        if (executorService instanceof MdcExecutorService) {
            return executorService;
        }
        return new MdcExecutorService(executorService);
    }

    /**
     * 包装 ScheduledExecutorService：其所有提交/调度方法都会自动携带 MDC 快照。
     *
     * @param scheduledExecutorService 原始 ScheduledExecutorService
     * @return 委托包装器；若已是包装器则原样返回
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService scheduledExecutorService) {
        if (scheduledExecutorService instanceof MdcScheduledExecutorService) {
            return scheduledExecutorService;
        }
        return new MdcScheduledExecutorService(scheduledExecutorService);
    }

    /**
     * 批量包装 Callable 集合，供 invokeAll/invokeAny 等批量提交方法复用。
     *
     * @param tasks 原始任务集合
     * @param <T>   返回值类型
     * @return 逐一包装后的任务集合
     */
    static <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        List<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            wrapped.add(wrap(task));
        }
        return wrapped;
    }

    /**
     * 将给定 MDC 上下文应用到当前线程。
     *
     * @param context 目标上下文；为 null 表示提交时 MDC 为空，需清空当前线程 MDC
     */
    static void apply(Map<String, String> context) {
        // 快照为 null 说明提交线程当时没有任何 MDC，此时应清空目标线程，避免残留旧值
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
