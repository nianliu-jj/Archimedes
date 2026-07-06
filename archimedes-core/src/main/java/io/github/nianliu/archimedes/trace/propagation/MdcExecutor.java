package io.github.nianliu.archimedes.trace.propagation;

import java.util.concurrent.Executor;

/**
 * Executor 委托包装器：提交任务时快照 MDC。经 MdcWrappers.wrap(Executor) 创建。
 *
 * <p>设计要点：对原始 Executor 做透明委托，仅在 execute 处插入 MDC 包装，
 * 不改变原有线程调度行为。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
class MdcExecutor implements Executor {

    /** 被包装的原始 Executor，所有调用最终委托给它。 */
    private final Executor delegate;

    MdcExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * 提交任务前先用 MDC 快照包装，保证子线程能拿到提交线程的 traceId。
     */
    @Override
    public void execute(Runnable command) {
        delegate.execute(MdcWrappers.wrap(command));
    }
}
