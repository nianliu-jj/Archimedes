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
 *
 * <p>设计要点：区分「前置注入 decorator」与「后置替换 Bean」两种策略——
 * ThreadPoolTaskExecutor 有强类型注入需求，只能在初始化前改其 decorator 属性而不能替换实例；
 * 纯接口 Executor 则可在初始化后安全地用委托包装器整体替换。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class MdcExecutorBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(MdcExecutorBeanPostProcessor.class);

    /** 按 Bean 名排除的集合，命中者不做任何 MDC 接入（宿主逃生口）。 */
    private final Set<String> excludeBeans;

    /**
     * @param excludeBeans 需排除的 Bean 名集合，来自配置 exclude-beans
     */
    public MdcExecutorBeanPostProcessor(Collection<String> excludeBeans) {
        this.excludeBeans = new HashSet<>(excludeBeans);
    }

    /**
     * 初始化前处理：仅针对 ThreadPoolTaskExecutor 注入 MdcTaskDecorator。
     *
     * <p>之所以在「初始化前」处理，是因为要在 executor 真正 initialize（创建底层线程池）
     * 之前把 decorator 设置好，确保后续提交的任务都经过装饰。
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor && !excludeBeans.contains(beanName)) {
            injectDecorator((ThreadPoolTaskExecutor) bean, beanName);
        }
        return bean;
    }

    /**
     * 初始化后处理：对纯接口形态的 Executor Bean 用委托包装器替换。
     *
     * <p>按 ScheduledExecutorService → ExecutorService → Executor 从具体到抽象的顺序判断，
     * 保证用最贴合的包装器接管，避免调度类方法丢失 MDC 传递。
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 排除名单：直接返回原 Bean，不做任何接入
        if (excludeBeans.contains(beanName)) {
            return bean;
        }
        // ThreadPoolTaskExecutor 已在前置阶段用 decorator 处理；TaskScheduler 是定时任务，
        // 不源于请求上下文，无需传递 MDC，两者均跳过
        if (bean instanceof ThreadPoolTaskExecutor || bean instanceof TaskScheduler) {
            return bean;
        }
        // 幂等：已是本框架的包装器，避免二次包装
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

    /**
     * 为 ThreadPoolTaskExecutor 注入 MdcTaskDecorator，已有用户 decorator 时做组合而非覆盖。
     *
     * @param executor 目标 executor
     * @param beanName Bean 名，用于日志
     */
    private void injectDecorator(ThreadPoolTaskExecutor executor, String beanName) {
        final TaskDecorator existing = readExistingDecorator(executor, beanName);
        final MdcTaskDecorator mdcDecorator = new MdcTaskDecorator();
        // 无既有 decorator 或既有即本框架的，直接设置，避免重复叠加
        if (existing == null || existing instanceof MdcTaskDecorator) {
            executor.setTaskDecorator(mdcDecorator);
        } else {
            // 存在用户自定义 decorator：组合两者——先应用用户装饰，再套 MDC 装饰，
            // 保证用户逻辑与 MDC 传递都生效且不相互覆盖
            executor.setTaskDecorator(new TaskDecorator() {
                @Override
                public Runnable decorate(Runnable runnable) {
                    return mdcDecorator.decorate(existing.decorate(runnable));
                }
            });
        }
    }

    /**
     * 反射读取 ThreadPoolTaskExecutor 私有的 taskDecorator 字段，用于组合判断。
     *
     * <p>Spring 未暴露该字段的 getter，只能反射读取；读取失败时降级为「视为无既有 decorator」，
     * 此时用户 decorator（若有）会被 MdcTaskDecorator 覆盖，故打 debug 日志留痕。
     *
     * @return 既有 decorator；不存在或读取失败时返回 null
     */
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
