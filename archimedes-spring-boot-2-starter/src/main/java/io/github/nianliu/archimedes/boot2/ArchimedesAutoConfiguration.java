package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * Spring Boot 2.7.x 侧薄注册层。用经典 @Configuration + @AutoConfigureAfter 而非 2.7 新增的
 * &#64;AutoConfiguration，注册走 spring.factories，与 2.7.x 全系兼容。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ArchimedesApiProperties.class)
public class ArchimedesAutoConfiguration {

    @Bean
    public RestApiScanner archimedesRestApiScanner(List<RequestMappingHandlerMapping> handlerMappings,
                                                   ArchimedesApiProperties properties) {
        return new RestApiScanner(handlerMappings, properties);
    }

    @Bean
    public ArchimedesApiController archimedesApiController(RestApiScanner scanner,
                                                           ArchimedesApiProperties properties) {
        return new ArchimedesApiController(scanner, properties);
    }
}
