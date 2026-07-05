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
 */
public class AsyncCoverageAdvisor implements SmartInitializingSingleton, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(AsyncCoverageAdvisor.class);

    private static final String ASYNC_ANNOTATION_BPP =
            "org.springframework.context.annotation.internalAsyncAnnotationProcessor";
    private static final String DEFAULT_TASK_EXECUTOR_BEAN_NAME = "taskExecutor";

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (beanFactory == null || !beanFactory.containsBean(ASYNC_ANNOTATION_BPP)) {
            return;
        }
        String[] taskExecutors = beanFactory.getBeanNamesForType(TaskExecutor.class, true, false);
        boolean unique = taskExecutors.length == 1;
        boolean namedPresent = beanFactory.containsBean(DEFAULT_TASK_EXECUTOR_BEAN_NAME);
        if (!unique && !namedPresent) {
            log.warn("Archimedes: @EnableAsync 已启用，但容器中没有唯一的 TaskExecutor Bean，"
                    + "也没有名为 'taskExecutor' 的 Bean（当前 TaskExecutor Bean: {} 个）。"
                    + "@Async 将退化为非容器管理的 SimpleAsyncTaskExecutor，"
                    + "traceId 的 MDC 跨线程自动传递无法覆盖该场景——"
                    + "请定义一个名为 'taskExecutor' 的 ThreadPoolTaskExecutor Bean。", taskExecutors.length);
        }
    }
}
