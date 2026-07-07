package io.github.nianliu.archimedes.boot2;

import com.zaxxer.hikari.HikariDataSource;
import io.github.nianliu.archimedes.jdbc.DataSourceMonitorRegistry;
import io.github.nianliu.archimedes.jdbc.MonitoringDataSource;
import io.github.nianliu.archimedes.jdbc.PoolMetricsContributor;
import io.github.nianliu.archimedes.jdbc.SqlStatRegistry;
import io.github.nianliu.archimedes.web.ArchimedesDbController;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesSqlMonitorAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ArchimedesSqlMonitorAutoConfiguration.class));

    @Test
    void registersMonitorBeansAndWrapsDataSource() {
        runner.withUserConfiguration(DataSourceConfig.class).run(context -> {
            assertThat(context).hasSingleBean(SqlStatRegistry.class);
            assertThat(context).hasSingleBean(DataSourceMonitorRegistry.class);
            assertThat(context).hasSingleBean(ArchimedesDbController.class);
            // 测试 classpath 有 HikariCP → 池指标贡献者装配
            assertThat(context).hasSingleBean(PoolMetricsContributor.class);
            // 数据源被 BPP 包装并登记
            assertThat(context.getBean("h2Ds", DataSource.class)).isInstanceOf(MonitoringDataSource.class);
            assertThat(context.getBean(DataSourceMonitorRegistry.class).list()).hasSize(1);
        });
    }

    @Test
    void respectsExcludeBeans() {
        runner.withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("archimedes.sql.exclude-beans=h2Ds")
                .run(context -> {
                    assertThat(context.getBean("h2Ds", DataSource.class))
                            .isNotInstanceOf(MonitoringDataSource.class);
                    assertThat(context.getBean(DataSourceMonitorRegistry.class).list()).isEmpty();
                });
    }

    @Test
    void skipsWhenSqlSwitchOff() {
        runner.withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues("archimedes.sql.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ArchimedesDbController.class);
                    assertThat(context.getBean("h2Ds", DataSource.class))
                            .isNotInstanceOf(MonitoringDataSource.class);
                });
    }

    @Test
    void skipsWhenApiTotalSwitchOff() {
        runner.withPropertyValues("archimedes.api.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ArchimedesDbController.class));
    }

    @Test
    void skipsInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesSqlMonitorAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(ArchimedesDbController.class));
    }

    @Test
    void degradesGracefullyWithoutHikariOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(HikariDataSource.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ArchimedesDbController.class);
                    // Hikari 缺席：池指标贡献者不装配，其余功能不受影响
                    assertThat(context.getBeansOfType(PoolMetricsContributor.class)).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceConfig {

        @Bean
        DataSource h2Ds() {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:autoCfg" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            return h2;
        }
    }
}
