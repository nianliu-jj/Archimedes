package io.github.nianliu.archimedes.trace.propagation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ExecutorService 委托包装器：全部提交方法均快照 MDC。经 MdcWrappers.wrap(ExecutorService) 创建。
 *
 * <p>设计要点：逐一覆写所有会「提交任务」的方法（execute/submit/invokeAll/invokeAny）并插入
 * MDC 包装，而生命周期管理方法（shutdown/isTerminated 等）纯透明委托，不做任何干预。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
class MdcExecutorService implements ExecutorService {

    /** 被包装的原始 ExecutorService，所有调用最终委托给它。 */
    private final ExecutorService delegate;

    MdcExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(MdcWrappers.wrap(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(MdcWrappers.wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(MdcWrappers.wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(MdcWrappers.wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        // 批量提交需逐一包装，wrapAll 内部对每个任务分别快照当前 MDC
        return delegate.invokeAll(MdcWrappers.wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(MdcWrappers.wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(MdcWrappers.wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(MdcWrappers.wrapAll(tasks), timeout, unit);
    }

    // 以下为生命周期管理方法，与 MDC 无关，纯透明委托给原始 delegate

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
