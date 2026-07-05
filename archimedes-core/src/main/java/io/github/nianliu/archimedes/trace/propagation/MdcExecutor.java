package io.github.nianliu.archimedes.trace.propagation;

import java.util.concurrent.Executor;

/** Executor 委托包装器：提交任务时快照 MDC。经 MdcWrappers.wrap(Executor) 创建。 */
class MdcExecutor implements Executor {

    private final Executor delegate;

    MdcExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(MdcWrappers.wrap(command));
    }
}
