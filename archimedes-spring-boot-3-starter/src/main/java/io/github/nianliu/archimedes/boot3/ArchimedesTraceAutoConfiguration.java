package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.trace.TraceContextManager;
import io.github.nianliu.archimedes.trace.TraceIdGenerator;
import io.github.nianliu.archimedes.trace.TraceIdResolver;
import io.github.nianliu.archimedes.trace.TraceProperties;
import io.github.nianliu.archimedes.trace.UuidTraceIdGenerator;
import io.github.nianliu.archimedes.trace.propagation.AsyncCoverageAdvisor;
import io.github.nianliu.archimedes.trace.propagation.MdcExecutorBeanPostProcessor;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;

/**
 * 链路追踪（trace）自动装配（SERVLET 栈）：装配 traceId 生成器、上下文管理器、请求入口 Filter，
 * 以及跨线程 MDC 传播的两件套（BeanPostProcessor 包装线程池 + AOP 兜底 Advisor）。
 * 仅 Servlet 栈提供；响应式栈需 Reactor Context 传播，暂不在此装配。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@AutoConfiguration
// 仅 Servlet 型 Web 应用装配（依赖 Servlet Filter 机制建立请求级 trace 上下文）
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 允许通过 archimedes.trace.enabled=false 关闭整套链路追踪；缺省开启
@ConditionalOnProperty(prefix = "archimedes.trace", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties.class)
public class ArchimedesTraceAutoConfiguration {

    /** traceId 生成器默认实现（UUID）；宿主可注册自己的 TraceIdGenerator Bean 覆盖。 */
    @Bean
    @ConditionalOnMissingBean // 让位点：宿主自定义生成策略时不再装配默认实现
    public TraceIdGenerator archimedesTraceIdGenerator() {
        return new UuidTraceIdGenerator();
    }

    /** trace 上下文管理器：封装“从请求头解析或新生成 traceId → 写入 MDC → 收尾清理”的核心编排；TraceIdResolver 可选。 */
    @Bean
    public TraceContextManager archimedesTraceContextManager(TraceProperties properties,
                                                             TraceIdGenerator generator,
                                                             ObjectProvider<TraceIdResolver> resolver) {
        return new TraceContextManager(properties, generator, resolver.getIfAvailable());
    }

    /** 注册请求入口 Filter：每个请求进入时建立 trace 上下文、离开时清理。 */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> archimedesTraceIdFilter(TraceContextManager manager,
                                                                         TraceProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter(manager, properties));
        // trace 上下文必须先于一切业务 Filter 建立；异步二次分发交由跨线程传递机制处理
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST); // 仅初始 REQUEST 分发建 trace，避免 ASYNC/FORWARD 重复建立
        registration.addUrlPatterns("/*"); // 覆盖全部请求路径
        registration.setName("archimedesTraceIdFilter");
        return registration;
    }

    /**
     * 跨线程 MDC 传播——包装容器内线程池 Bean，使子线程能继承父线程 traceId。
     * static + Environment/Binder 取配置：BPP 必须早注册，不能连带拉起 @ConfigurationProperties 绑定链。
     */
    @Bean
    @ConditionalOnProperty(prefix = "archimedes.trace.propagation", name = "enabled", matchIfMissing = true)
    public static MdcExecutorBeanPostProcessor archimedesMdcExecutorBeanPostProcessor(Environment environment) {
        // 手动 Binder 绑定排除名单，规避在 BPP 阶段过早触发 @ConfigurationProperties 绑定链
        List<String> excludeBeans = Binder.get(environment)
                .bind("archimedes.trace.propagation.exclude-beans", Bindable.listOf(String.class))
                .orElseGet(Collections::emptyList);
        return new MdcExecutorBeanPostProcessor(excludeBeans);
    }

    /** 异步覆盖兜底：对 @Async 等未被 BPP 包装到的异步执行点，用 AOP 在调用边界补齐 MDC 传播。 */
    @Bean
    @ConditionalOnProperty(prefix = "archimedes.trace.propagation", name = "enabled", matchIfMissing = true)
    public AsyncCoverageAdvisor archimedesAsyncCoverageAdvisor() {
        return new AsyncCoverageAdvisor();
    }
}
