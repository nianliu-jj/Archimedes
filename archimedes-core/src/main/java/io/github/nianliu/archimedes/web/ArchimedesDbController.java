package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.jdbc.DataSourceMonitorRegistry;
import io.github.nianliu.archimedes.jdbc.MonitoringDataSource;
import io.github.nianliu.archimedes.jdbc.PoolMetricsContributor;
import io.github.nianliu.archimedes.jdbc.SqlStatRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库监控端点：{@code GET {base-path}/db} 返回数据源列表（含连接池指标）、
 * SQL 聚合统计、最近执行明细与慢 SQL 明细。
 * 纯注解式控制器，零 servlet 依赖，Servlet 与 WebFlux 两栈复用；
 * 路径位于 {@code {base-path}} 下，天然被契约扫描的 base-path 排除规则覆盖。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@RestController
public class ArchimedesDbController {

    private final DataSourceMonitorRegistry monitorRegistry;
    private final SqlStatRegistry statRegistry;
    /** 池指标贡献者（Hikari 内置实现按 classpath 条件装配，可能为空列表）。 */
    private final List<PoolMetricsContributor> poolMetricsContributors;

    public ArchimedesDbController(DataSourceMonitorRegistry monitorRegistry,
                                  SqlStatRegistry statRegistry,
                                  List<PoolMetricsContributor> poolMetricsContributors) {
        this.monitorRegistry = monitorRegistry;
        this.statRegistry = statRegistry;
        this.poolMetricsContributors = poolMetricsContributors == null
                ? Collections.<PoolMetricsContributor>emptyList()
                : poolMetricsContributors;
    }

    /** 数据库监控总览：数据源/池指标 + SQL 统计 + 最近执行 + 慢 SQL。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/db",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> db() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("slowSqlMillis", statRegistry.getProperties().getSlowSqlMillis());
        body.put("dataSources", dataSourceViews());
        body.put("sqlStats", statRegistry.statViews());
        body.put("recentSqls", statRegistry.recentSnapshot());
        body.put("slowSqls", statRegistry.slowSnapshot());
        return body;
    }

    /** 数据源视图：Bean 名、目标类型、第一个命中的池指标贡献者输出（无命中为 null）。 */
    private List<Map<String, Object>> dataSourceViews() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (MonitoringDataSource dataSource : monitorRegistry.list()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", dataSource.getBeanName());
            view.put("targetType", dataSource.getTargetDataSource().getClass().getName());
            view.put("pool", poolMetrics(dataSource.getTargetDataSource()));
            views.add(view);
        }
        return views;
    }

    private Map<String, Object> poolMetrics(DataSource target) {
        for (PoolMetricsContributor contributor : poolMetricsContributors) {
            try {
                if (contributor.supports(target)) {
                    return contributor.metrics(target);
                }
            } catch (RuntimeException ignore) {
                // 单个贡献者异常不影响端点整体输出
            }
        }
        return null;
    }
}
