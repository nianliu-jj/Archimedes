package io.github.nianliu.archimedes.jdbc;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 连接池指标贡献者 SPI：按数据源输出池运行指标（活跃/空闲/等待等）。
 * <p>内置 {@code HikariPoolMetricsContributor}（classpath 存在 HikariCP 时装配）；
 * 宿主可注册自己的实现接入其它连接池（Druid/DBCP2 等），端点对每个数据源
 * 取第一个 {@link #supports(DataSource)} 命中的贡献者输出。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
public interface PoolMetricsContributor {

    /**
     * 是否支持该数据源（入参为未包装的目标数据源）。
     */
    boolean supports(DataSource dataSource);

    /**
     * 输出池指标键值（仅在 supports 返回 true 时调用）。
     */
    Map<String, Object> metrics(DataSource dataSource);
}
