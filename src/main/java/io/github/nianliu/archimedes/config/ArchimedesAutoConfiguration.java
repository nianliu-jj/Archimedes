package io.github.nianliu.archimedes.config;

import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
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
