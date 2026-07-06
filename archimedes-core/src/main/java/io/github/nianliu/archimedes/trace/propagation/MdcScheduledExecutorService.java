package io.github.nianliu.archimedes.trace.propagation;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ScheduledExecutorService 委托包装器。经 MdcWrappers.wrap(ScheduledExecutorService) 创建。
 *
 * <p>设计要点：继承 {@link MdcExecutorService} 复用其对 execute/submit 等方法的包装，
 * 仅额外覆写四个 schedule 系列方法，为定时/周期任务补上 MDC 传递。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
class MdcScheduledExecutorService extends MdcExecutorService implements ScheduledExecutorService {

    /** 被包装的原始 ScheduledExecutorService，用于 schedule 系列方法委托。 */
    private final ScheduledExecutorService delegate;

    MdcScheduledExecutorService(ScheduledExecutorService delegate) {
        // 父类持有同一 delegate 以复用非调度类方法的包装逻辑
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return delegate.schedule(MdcWrappers.wrap(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return delegate.schedule(MdcWrappers.wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return delegate.scheduleAtFixedRate(MdcWrappers.wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return delegate.scheduleWithFixedDelay(MdcWrappers.wrap(command), initialDelay, delay, unit);
    }
}
