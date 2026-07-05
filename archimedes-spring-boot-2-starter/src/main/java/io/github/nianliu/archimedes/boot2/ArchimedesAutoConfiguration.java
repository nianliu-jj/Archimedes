package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.scanner.ws.SpringWebSocketHandlerScanner;
import io.github.nianliu.archimedes.scanner.ws.StompMappingScanner;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;

import java.util.List;
import java.util.stream.Collectors;

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
                                                           ArchimedesApiProperties properties,
                                                           ObjectProvider<WebSocketApiContributor> contributors) {
        return new ArchimedesApiController(scanner, properties,
                contributors.orderedStream().collect(Collectors.toList()));
    }

    /** 宿主存在 spring-websocket 时装配 handler 端点扫描（含 STOMP 握手端点识别）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebSocketHandler.class)
    static class WebSocketHandlerScanConfiguration {

        @Bean
        public SpringWebSocketHandlerScanner archimedesWebSocketHandlerScanner(
                ObjectProvider<SimpleUrlHandlerMapping> handlerMappings) {
            return new SpringWebSocketHandlerScanner(
                    handlerMappings.orderedStream().collect(Collectors.toList()));
        }
    }

    /** 宿主存在 spring-messaging 时装配 STOMP 注解映射扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SimpAnnotationMethodMessageHandler.class)
    static class StompScanConfiguration {

        @Bean
        public StompMappingScanner archimedesStompMappingScanner(
                ObjectProvider<SimpAnnotationMethodMessageHandler> messageHandlers) {
            return new StompMappingScanner(
                    messageHandlers.orderedStream().collect(Collectors.toList()));
        }
    }

    /** 宿主存在 javax.websocket API 时装配 @ServerEndpoint 扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(javax.websocket.server.ServerEndpoint.class)
    static class ServerEndpointScanConfiguration {

        @Bean
        public Boot2ServerEndpointScanner archimedesServerEndpointScanner(ApplicationContext applicationContext) {
            return new Boot2ServerEndpointScanner(applicationContext);
        }
    }
}
