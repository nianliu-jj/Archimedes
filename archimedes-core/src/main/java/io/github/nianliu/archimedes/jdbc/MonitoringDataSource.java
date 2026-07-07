package io.github.nianliu.archimedes.jdbc;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * 监控数据源：包装宿主任意 {@link DataSource}，对 {@link #getConnection()} 返回的连接
 * 挂 JDK 动态代理，逐层拦截 Statement 执行以记录 SQL 明细与统计。
 * <p>兼容性要点：{@link #unwrap(Class)} / {@link #isWrapperFor(Class)} 完整透传目标数据源，
 * Spring Boot 的 DataSourceUnwrapper、actuator、Flyway 等经 Wrapper 链仍可触达原生实现
 * （如 HikariDataSource）；按具体类型注入原生数据源的宿主可用
 * {@code archimedes.sql.exclude-beans} 排除包装。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class MonitoringDataSource implements DataSource {

    /** 被包装的目标数据源。 */
    private final DataSource target;
    /** 数据源 Bean 名（多数据源区分维度）。 */
    private final String beanName;
    /** 统计注册表（含配置）。 */
    private final SqlStatRegistry statRegistry;
    /** 读取 traceId 的 MDC key（与链路日志关联）。 */
    private final String traceIdMdcKey;

    public MonitoringDataSource(DataSource target, String beanName,
                                SqlStatRegistry statRegistry, String traceIdMdcKey) {
        this.target = target;
        this.beanName = beanName;
        this.statRegistry = statRegistry;
        this.traceIdMdcKey = traceIdMdcKey;
    }

    public DataSource getTargetDataSource() {
        return target;
    }

    public String getBeanName() {
        return beanName;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(target.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(target.getConnection(username, password));
    }

    /** 对物理连接挂动态代理，拦截 createStatement/prepareStatement/prepareCall。 */
    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                MonitoringDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new MonitoringConnectionHandler(connection, beanName, statRegistry, traceIdMdcKey));
    }

    /** unwrap 透传：自身命中直接返回，否则委托目标数据源（保证 Wrapper 链可达原生实现）。 */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || target.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return target.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        target.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        target.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return target.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return target.getParentLogger();
    }
}
