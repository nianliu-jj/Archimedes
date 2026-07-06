package io.github.nianliu.archimedes.trace.propagation;

import org.springframework.core.task.TaskDecorator;

/**
 * Spring TaskDecorator 形态的 MDC 传递接入，用于 ThreadPoolTaskExecutor（保持 Bean 类型不变）。
 *
 * <p>设计要点：相比直接替换 Bean 为委托包装器，采用 TaskDecorator 能保留原 Bean 的
 * 具体类型（ThreadPoolTaskExecutor），避免破坏宿主对该类型的强依赖注入。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * 用 MDC 传递逻辑装饰任务：提交线程快照 MDC，执行线程恢复后再执行。
     *
     * @param runnable 原始任务
     * @return 携带 MDC 快照的包装任务
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcWrappers.wrap(runnable);
    }
}
