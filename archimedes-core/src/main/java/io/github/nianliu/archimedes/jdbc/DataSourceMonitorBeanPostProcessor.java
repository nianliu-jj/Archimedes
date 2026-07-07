package io.github.nianliu.archimedes.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据源监控 BeanPostProcessor：初始化后将容器内 {@link DataSource} Bean
 * 包装为 {@link MonitoringDataSource} 并登记，SQL 执行自此被拦截记录。
 * <ul>
 *   <li>已是监控包装的、exclude-beans 命中的不重复包装；</li>
 *   <li>协作对象（统计/登记注册表）经 {@link ObjectProvider} 惰性解析——BPP 注册极早，
 *       不能在构造期拉起其它 Bean（对齐 MdcExecutorBeanPostProcessor 先例）；</li>
 *   <li>注册表尚不可解析时放行原 Bean（防御式，不阻断启动）。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
public class DataSourceMonitorBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataSourceMonitorBeanPostProcessor.class);

    private final ObjectProvider<SqlStatRegistry> statRegistryProvider;
    private final ObjectProvider<DataSourceMonitorRegistry> monitorRegistryProvider;
    /** 排除包装的数据源 Bean 名（宿主逃生口，如需按具体类型注入原生 HikariDataSource）。 */
    private final Set<String> excludeBeans;
    /** 读取 traceId 的 MDC key（与 archimedes.trace.mdc-key 一致）。 */
    private final String traceIdMdcKey;

    public DataSourceMonitorBeanPostProcessor(ObjectProvider<SqlStatRegistry> statRegistryProvider,
                                              ObjectProvider<DataSourceMonitorRegistry> monitorRegistryProvider,
                                              Collection<String> excludeBeans,
                                              String traceIdMdcKey) {
        this.statRegistryProvider = statRegistryProvider;
        this.monitorRegistryProvider = monitorRegistryProvider;
        this.excludeBeans = new HashSet<>(excludeBeans);
        this.traceIdMdcKey = traceIdMdcKey;
    }

    /** 初始化后包装：此时连接池等 Bean 已完成自身初始化，包装不影响其生命周期回调。 */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource) || bean instanceof MonitoringDataSource) {
            return bean;
        }
        if (excludeBeans.contains(beanName)) {
            log.info("数据源 Bean {} 命中 archimedes.sql.exclude-beans，跳过 SQL 监控包装", beanName);
            return bean;
        }
        SqlStatRegistry statRegistry = statRegistryProvider.getIfAvailable();
        DataSourceMonitorRegistry monitorRegistry = monitorRegistryProvider.getIfAvailable();
        if (statRegistry == null || monitorRegistry == null) {
            // 注册表 Bean 不可用（如宿主自定义装配裁剪）：防御式放行，不阻断启动
            log.warn("SQL 监控注册表不可用，数据源 Bean {} 未包装", beanName);
            return bean;
        }
        MonitoringDataSource wrapped =
                new MonitoringDataSource((DataSource) bean, beanName, statRegistry, traceIdMdcKey);
        monitorRegistry.register(beanName, wrapped);
        log.info("数据源 Bean {} 已接入 SQL 监控 (target={})", beanName, bean.getClass().getName());
        return wrapped;
    }
}
