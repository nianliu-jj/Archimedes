package io.github.nianliu.archimedes.jdbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已监控数据源登记表：BeanPostProcessor 包装数据源时登记，
 * 端点控制器据此枚举数据源列表并读取池指标。
 * 注册发生在容器单例创建期（可能并发），读写全程同步；条目保持登记顺序。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
public class DataSourceMonitorRegistry {

    /** beanName → 监控包装数据源（保持登记顺序）。 */
    private final Map<String, MonitoringDataSource> dataSources = new LinkedHashMap<>();

    /** 登记一个被包装的数据源。 */
    public synchronized void register(String beanName, MonitoringDataSource dataSource) {
        dataSources.put(beanName, dataSource);
    }

    /** 全部已监控数据源快照（登记顺序）。 */
    public synchronized List<MonitoringDataSource> list() {
        return new ArrayList<>(dataSources.values());
    }
}
