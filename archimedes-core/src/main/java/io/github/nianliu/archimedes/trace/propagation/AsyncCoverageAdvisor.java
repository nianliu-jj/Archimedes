package io.github.nianliu.archimedes.trace.propagation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.task.TaskExecutor;

/**
 * 覆盖边界启动检测：@EnableAsync 生效但容器中既无唯一 TaskExecutor Bean、也无名为
 * "taskExecutor" 的 Bean 时，Spring 会退化到非 Bean 的 SimpleAsyncTaskExecutor——
 * 它不经过容器，MDC 自动传递覆盖不到。此时打 WARN 提示宿主定义 taskExecutor Bean。
 * （典型触发场景：宿主定义了任意 Executor Bean 使 Boot 的 applicationTaskExecutor 退避，
 * 例如启用 STOMP 后其内部通道执行器 Bean。）
 *
 * <p>设计要点：实现 {@link SmartInitializingSingleton}，在所有单例实例化完成后才检测，
 * 确保能看到容器中完整的 TaskExecutor Bean 全景，避免过早检测得出错误结论。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class AsyncCoverageAdvisor implements SmartInitializingSingleton, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(AsyncCoverageAdvisor.class);

    /** Spring 内部 @Async 注解处理器的 Bean 名，用其存在与否判断 @EnableAsync 是否生效。 */
    private static final String ASYNC_ANNOTATION_BPP =
            "org.springframework.context.annotation.internalAsyncAnnotationProcessor";
    /** Spring @Async 默认查找的 executor Bean 名。 */
    private static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME = "taskExecutor";

    private ConfigurableListableBeanFactory beanFactory;

    /**
     * 注入 BeanFactory；仅在其为可枚举类型时保留，以便后续按类型/按名查询 Bean。
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    /**
     * 所有单例实例化完成后执行：检测 @Async 的 executor 解析是否会落空并 WARN 提示。
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 未启用 @EnableAsync（无内部处理器 Bean）则无需检测，直接返回
        if (beanFactory == null || !beanFactory.containsBean(ASYNC_ANNOTATION_BPP)) {
            return;
        }
        // 复现 Spring @Async 的 executor 解析规则：优先唯一 TaskExecutor Bean，其次名为 taskExecutor 的 Bean
        String[] taskExecutors = beanFactory.getBeanNamesForType(TaskExecutor.class, true, false);
        boolean unique = taskExecutors.length == 1;
        boolean namedPresent = beanFactory.containsBean(DEFAULT_TASK_EXECUTOR_BEAN_NAME);
        // 两条解析路径都不满足 → Spring 将退化为非容器管理的 SimpleAsyncTaskExecutor，MDC 传递覆盖不到
        if (!unique && !namedPresent) {
            log.warn("Archimedes: @EnableAsync 已启用，但容器中没有唯一的 TaskExecutor Bean，"
                    + "也没有名为 'taskExecutor' 的 Bean（当前 TaskExecutor Bean: {} 个）。"
                    + "@Async 将退化为非容器管理的 SimpleAsyncTaskExecutor，"
                    + "traceId 的 MDC 跨线程自动传递无法覆盖该场景——"
                    + "请定义一个名为 'taskExecutor' 的 ThreadPoolTaskExecutor Bean。", taskExecutors.length);
        }
    }
}
