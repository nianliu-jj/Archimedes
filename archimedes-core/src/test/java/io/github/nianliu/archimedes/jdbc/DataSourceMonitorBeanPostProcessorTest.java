package io.github.nianliu.archimedes.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceMonitorBeanPostProcessorTest {

    @Test
    void wrapsDataSourceBeansAndRegisters() {
        new ApplicationContextRunner()
                .withUserConfiguration(WrapConfig.class)
                .run(context -> {
                    DataSource ds = context.getBean("h2Ds", DataSource.class);
                    assertThat(ds).isInstanceOf(MonitoringDataSource.class);
                    assertThat(((MonitoringDataSource) ds).getBeanName()).isEqualTo("h2Ds");
                    // 登记表可枚举到该数据源
                    DataSourceMonitorRegistry registry = context.getBean(DataSourceMonitorRegistry.class);
                    assertThat(registry.list()).hasSize(1);
                    // Wrapper 链可达原生实现
                    assertThat(ds.unwrap(JdbcDataSource.class)).isInstanceOf(JdbcDataSource.class);
                });
    }

    @Test
    void respectsExcludeBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(ExcludeConfig.class)
                .run(context -> {
                    DataSource ds = context.getBean("h2Ds", DataSource.class);
                    assertThat(ds).isNotInstanceOf(MonitoringDataSource.class);
                    assertThat(context.getBean(DataSourceMonitorRegistry.class).list()).isEmpty();
                });
    }

    @Test
    void ignoresNonDataSourceBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(WrapConfig.class)
                .run(context -> assertThat(context.getBean("plain", String.class)).isEqualTo("bean"));
    }

    static DataSource newH2() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:bppTest" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return h2;
    }

    @Configuration(proxyBeanMethods = false)
    static class WrapConfig {

        @Bean
        SqlMonitorProperties sqlMonitorProperties() {
            return new SqlMonitorProperties();
        }

        @Bean
        SqlStatRegistry sqlStatRegistry(SqlMonitorProperties properties) {
            return new SqlStatRegistry(properties);
        }

        @Bean
        DataSourceMonitorRegistry dataSourceMonitorRegistry() {
            return new DataSourceMonitorRegistry();
        }

        @Bean
        static DataSourceMonitorBeanPostProcessor bpp(ObjectProvider<SqlStatRegistry> stats,
                                                      ObjectProvider<DataSourceMonitorRegistry> monitors) {
            return new DataSourceMonitorBeanPostProcessor(stats, monitors,
                    Collections.emptyList(), "traceId");
        }

        @Bean
        DataSource h2Ds() {
            return newH2();
        }

        @Bean
        String plain() {
            return "bean";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExcludeConfig {

        @Bean
        SqlMonitorProperties sqlMonitorProperties() {
            return new SqlMonitorProperties();
        }

        @Bean
        SqlStatRegistry sqlStatRegistry(SqlMonitorProperties properties) {
            return new SqlStatRegistry(properties);
        }

        @Bean
        DataSourceMonitorRegistry dataSourceMonitorRegistry() {
            return new DataSourceMonitorRegistry();
        }

        @Bean
        static DataSourceMonitorBeanPostProcessor bpp(ObjectProvider<SqlStatRegistry> stats,
                                                      ObjectProvider<DataSourceMonitorRegistry> monitors) {
            return new DataSourceMonitorBeanPostProcessor(stats, monitors,
                    Collections.singletonList("h2Ds"), "traceId");
        }

        @Bean
        DataSource h2Ds() {
            return newH2();
        }
    }
}
