package io.github.nianliu.archimedes.trace.propagation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 对容器内所有 Executor 形态的 Bean 自动接入 MDC 跨线程传递：
 * <ul>
 *   <li>ThreadPoolTaskExecutor：初始化前注入 MdcTaskDecorator（保持 Bean 类型不变；已有 decorator 则组合）</li>
 *   <li>ScheduledExecutorService / ExecutorService / Executor 接口 Bean：初始化后以同接口委托包装器替换</li>
 *   <li>TaskScheduler 形态：跳过（定时任务不源于请求上下文）</li>
 * </ul>
 * 可通过 archimedes.trace.propagation.exclude-beans 按 Bean 名排除。
 */
public class MdcExecutorBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(MdcExecutorBeanPostProcessor.class);

    private final Set<String> excludeBeans;

    public MdcExecutorBeanPostProcessor(Collection<String> excludeBeans) {
        this.excludeBeans = new HashSet<>(excludeBeans);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor && !excludeBeans.contains(beanName)) {
            injectDecorator((ThreadPoolTaskExecutor) bean, beanName);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (excludeBeans.contains(beanName)) {
            return bean;
        }
        if (bean instanceof ThreadPoolTaskExecutor || bean instanceof TaskScheduler) {
            return bean;
        }
        if (bean instanceof MdcExecutor || bean instanceof MdcExecutorService) {
            return bean;
        }
        if (bean instanceof ScheduledExecutorService) {
            return MdcWrappers.wrap((ScheduledExecutorService) bean);
        }
        if (bean instanceof ExecutorService) {
            return MdcWrappers.wrap((ExecutorService) bean);
        }
        if (bean instanceof Executor) {
            return MdcWrappers.wrap((Executor) bean);
        }
        return bean;
    }

    private void injectDecorator(ThreadPoolTaskExecutor executor, String beanName) {
        final TaskDecorator existing = readExistingDecorator(executor, beanName);
        final MdcTaskDecorator mdcDecorator = new MdcTaskDecorator();
        if (existing == null || existing instanceof MdcTaskDecorator) {
            executor.setTaskDecorator(mdcDecorator);
        } else {
            executor.setTaskDecorator(new TaskDecorator() {
                @Override
                public Runnable decorate(Runnable runnable) {
                    return mdcDecorator.decorate(existing.decorate(runnable));
                }
            });
        }
    }

    private TaskDecorator readExistingDecorator(ThreadPoolTaskExecutor executor, String beanName) {
        try {
            Field field = ReflectionUtils.findField(ThreadPoolTaskExecutor.class, "taskDecorator");
            if (field == null) {
                return null;
            }
            ReflectionUtils.makeAccessible(field);
            Object value = ReflectionUtils.getField(field, executor);
            return value instanceof TaskDecorator ? (TaskDecorator) value : null;
        } catch (Exception ex) {
            log.debug("Archimedes: cannot read existing taskDecorator of bean '{}', "
                    + "user decorator (if any) will be replaced by MdcTaskDecorator", beanName, ex);
            return null;
        }
    }
}
