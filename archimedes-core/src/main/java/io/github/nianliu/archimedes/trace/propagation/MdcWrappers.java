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
 */
public final class MdcWrappers {

    private MdcWrappers() {
    }

    public static Runnable wrap(final Runnable task) {
        final Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return new Runnable() {
            @Override
            public void run() {
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

    public static Executor wrap(Executor executor) {
        if (executor instanceof MdcExecutor) {
            return executor;
        }
        return new MdcExecutor(executor);
    }

    public static ExecutorService wrap(ExecutorService executorService) {
        if (executorService instanceof MdcExecutorService) {
            return executorService;
        }
        return new MdcExecutorService(executorService);
    }

    public static ScheduledExecutorService wrap(ScheduledExecutorService scheduledExecutorService) {
        if (scheduledExecutorService instanceof MdcScheduledExecutorService) {
            return scheduledExecutorService;
        }
        return new MdcScheduledExecutorService(scheduledExecutorService);
    }

    static <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        List<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            wrapped.add(wrap(task));
        }
        return wrapped;
    }

    static void apply(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
