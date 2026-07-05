package io.github.nianliu.archimedes.trace.propagation;

import org.springframework.core.task.TaskDecorator;

/**
 * Spring TaskDecorator 形态的 MDC 传递接入，用于 ThreadPoolTaskExecutor（保持 Bean 类型不变）。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcWrappers.wrap(runnable);
    }
}
