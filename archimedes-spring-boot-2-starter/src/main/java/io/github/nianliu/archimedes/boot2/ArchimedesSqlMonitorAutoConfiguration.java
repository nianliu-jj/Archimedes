package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.jdbc.DataSourceMonitorBeanPostProcessor;
import io.github.nianliu.archimedes.jdbc.DataSourceMonitorRegistry;
import io.github.nianliu.archimedes.jdbc.HikariPoolMetricsContributor;
import io.github.nianliu.archimedes.jdbc.PoolMetricsContributor;
import io.github.nianliu.archimedes.jdbc.SqlMonitorProperties;
import io.github.nianliu.archimedes.jdbc.SqlStatRegistry;
import io.github.nianliu.archimedes.web.ArchimedesDbController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库监控自动装配（SB2，经典 {@code @Configuration} + spring.factories 注册）：
 * 注册 SQL 统计注册表、数据源监控登记表、数据源包装 BeanPostProcessor、
 * Hikari 池指标贡献者（按 classpath 条件）与 DB 端点控制器。
 * 装配条件与 SB3 版一致：Web 应用不限类型 + api/sql 双开关；监控范围为 JDBC DataSource。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@Conditional(ArchimedesSqlMonitorAutoConfiguration.SqlMonitorEnabled.class)
@EnableConfigurationProperties(SqlMonitorProperties.class)
public class ArchimedesSqlMonitorAutoConfiguration {

    /** SQL 统计注册表：明细与聚合的唯一汇聚点。 */
    @Bean
    public SqlStatRegistry archimedesSqlStatRegistry(SqlMonitorProperties properties) {
        return new SqlStatRegistry(properties);
    }

    /** 已监控数据源登记表：端点枚举数据源与读取池指标的依据。 */
    @Bean
    public DataSourceMonitorRegistry archimedesDataSourceMonitorRegistry() {
        return new DataSourceMonitorRegistry();
    }

    /**
     * 数据源包装 BeanPostProcessor。
     * static + Environment/Binder 取配置：BPP 必须早注册，不能连带拉起 @ConfigurationProperties
     * 绑定链（对齐 MdcExecutorBeanPostProcessor 先例）；协作注册表经 ObjectProvider 惰性解析。
     */
    @Bean
    public static DataSourceMonitorBeanPostProcessor archimedesDataSourceMonitorBeanPostProcessor(
            ObjectProvider<SqlStatRegistry> statRegistry,
            ObjectProvider<DataSourceMonitorRegistry> monitorRegistry,
            Environment environment) {
        List<String> excludeBeans = Binder.get(environment)
                .bind("archimedes.sql.exclude-beans", Bindable.listOf(String.class))
                .orElseGet(Collections::emptyList);
        // 与链路日志同一 MDC key，SQL 明细据此关联 traceId
        String mdcKey = Binder.get(environment)
                .bind("archimedes.trace.mdc-key", String.class)
                .orElse("traceId");
        return new DataSourceMonitorBeanPostProcessor(statRegistry, monitorRegistry, excludeBeans, mdcKey);
    }

    /** DB 监控端点：池指标贡献者按序收集（Hikari 缺席时为空列表，池指标降级为 null）。 */
    @Bean
    public ArchimedesDbController archimedesDbController(DataSourceMonitorRegistry monitorRegistry,
                                                         SqlStatRegistry statRegistry,
                                                         ObjectProvider<PoolMetricsContributor> contributors) {
        return new ArchimedesDbController(monitorRegistry, statRegistry,
                contributors.orderedStream().collect(Collectors.toList()));
    }

    /** classpath 存在 HikariCP 时装配池指标贡献者（字符串条件：starter 不依赖 Hikari 编译面）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.zaxxer.hikari.HikariDataSource")
    static class HikariMetricsConfiguration {

        @Bean
        public PoolMetricsContributor archimedesHikariPoolMetricsContributor() {
            return new HikariPoolMetricsContributor();
        }
    }

    /** 双开关组合条件：archimedes.api.enabled 与 archimedes.sql.enabled 须同时开启。 */
    static class SqlMonitorEnabled extends AllNestedConditions {

        SqlMonitorEnabled() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
        static class ApiEnabled {
        }

        @ConditionalOnProperty(prefix = "archimedes.sql", name = "enabled", matchIfMissing = true)
        static class SqlSwitch {
        }
    }
}
