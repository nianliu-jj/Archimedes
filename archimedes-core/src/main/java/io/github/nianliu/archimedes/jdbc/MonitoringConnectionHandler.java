package io.github.nianliu.archimedes.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Connection 层代理处理器：拦截三族语句创建方法——
 * {@code createStatement} / {@code prepareStatement} / {@code prepareCall}，
 * 将返回的 Statement 族对象再包一层监控代理；其余方法（事务、元数据等）原样透传。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
class MonitoringConnectionHandler implements InvocationHandler {

    private final Connection target;
    private final String dataSourceName;
    private final SqlStatRegistry statRegistry;
    private final String traceIdMdcKey;

    MonitoringConnectionHandler(Connection target, String dataSourceName,
                                SqlStatRegistry statRegistry, String traceIdMdcKey) {
        this.target = target;
        this.dataSourceName = dataSourceName;
        this.statRegistry = statRegistry;
        this.traceIdMdcKey = traceIdMdcKey;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            Object result = method.invoke(target, args);
            // 语句创建方法：prepared 族首参即 SQL，createStatement 无预置 SQL（执行时从入参取）
            if (result instanceof Statement
                    && ("createStatement".equals(name)
                    || "prepareStatement".equals(name)
                    || "prepareCall".equals(name))) {
                String preparedSql = (args != null && args.length > 0 && args[0] instanceof String)
                        ? (String) args[0] : null;
                // 以方法声明的返回接口建代理（Statement/PreparedStatement/CallableStatement 正确分型）
                return Proxy.newProxyInstance(
                        MonitoringConnectionHandler.class.getClassLoader(),
                        new Class<?>[]{method.getReturnType()},
                        new MonitoringStatementHandler((Statement) result, preparedSql,
                                dataSourceName, statRegistry, traceIdMdcKey));
            }
            return result;
        } catch (InvocationTargetException ex) {
            // 还原目标方法抛出的原始异常，不改变调用方语义
            throw ex.getCause();
        }
    }
}
