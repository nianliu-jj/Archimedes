package io.github.nianliu.archimedes.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HikariCP 连接池指标贡献者：对可触达 {@link HikariDataSource} 的数据源输出
 * 活跃/空闲/等待线程/总连接运行指标与最大池/最小空闲配置。
 * <p>类内直引 HikariCP 类型（core 的 optional 依赖）——装配处以
 * {@code @ConditionalOnClass(HikariDataSource.class)} 守护，宿主未引 Hikari 时本类不加载
 * （对齐 Dubbo/gRPC 扫描器的可选依赖模式）。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
public class HikariPoolMetricsContributor implements PoolMetricsContributor {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolMetricsContributor.class);

    @Override
    public boolean supports(DataSource dataSource) {
        return unwrapHikari(dataSource) != null;
    }

    @Override
    public Map<String, Object> metrics(DataSource dataSource) {
        HikariDataSource hikari = unwrapHikari(dataSource);
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (hikari == null) {
            return metrics;
        }
        metrics.put("type", "HikariCP");
        metrics.put("poolName", hikari.getPoolName());
        metrics.put("maximumPoolSize", hikari.getMaximumPoolSize());
        metrics.put("minimumIdle", hikari.getMinimumIdle());
        // 池未启动（尚无连接获取）时 MXBean 为 null，只输出配置项
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool != null) {
            metrics.put("activeConnections", pool.getActiveConnections());
            metrics.put("idleConnections", pool.getIdleConnections());
            metrics.put("threadsAwaitingConnection", pool.getThreadsAwaitingConnection());
            metrics.put("totalConnections", pool.getTotalConnections());
        }
        return metrics;
    }

    /** 触达原生 HikariDataSource：instanceof 直判 → Wrapper.unwrap 兜底（防御式，失败返回 null）。 */
    private HikariDataSource unwrapHikari(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }
        try {
            if (dataSource != null && dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (Exception ex) {
            log.debug("unwrap HikariDataSource 失败（按不支持处理）: {}", ex.getMessage());
        }
        return null;
    }
}
