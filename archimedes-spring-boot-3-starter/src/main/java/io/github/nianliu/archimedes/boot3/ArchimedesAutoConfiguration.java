package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.scanner.rpc.DubboRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.GrpcRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.RpcApiContributor;
import io.github.nianliu.archimedes.scanner.rpc.SofaTrRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.TrpcRpcScanner;
import io.github.nianliu.archimedes.scanner.ws.SpringWebSocketHandlerScanner;
import io.github.nianliu.archimedes.scanner.ws.StompMappingScanner;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
                                                           ArchimedesApiProperties properties,
                                                           ObjectProvider<WebSocketApiContributor> contributors,
                                                           ObjectProvider<RpcApiContributor> rpcContributors) {
        return new ArchimedesApiController(scanner, properties,
                contributors.orderedStream().collect(Collectors.toList()),
                rpcContributors.orderedStream().collect(Collectors.toList()));
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

    /** 宿主存在 jakarta.websocket API 时装配 @ServerEndpoint 扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(jakarta.websocket.server.ServerEndpoint.class)
    static class ServerEndpointScanConfiguration {

        @Bean
        public Boot3ServerEndpointScanner archimedesServerEndpointScanner(ApplicationContext applicationContext) {
            return new Boot3ServerEndpointScanner(applicationContext);
        }
    }

    /** 宿主存在 Dubbo 时装配 provider 服务扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ServiceBean.class)
    static class DubboScanConfiguration {

        @Bean
        public DubboRpcScanner archimedesDubboRpcScanner(ApplicationContext applicationContext) {
            return new DubboRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 gRPC 时装配 BindableService 扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.grpc.BindableService.class)
    static class GrpcScanConfiguration {

        @Bean
        public GrpcRpcScanner archimedesGrpcRpcScanner(ApplicationContext applicationContext) {
            return new GrpcRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 SOFABoot（@SofaService）时装配 SOFARPC 服务扫描（字符串条件，零 SOFA 编译依赖）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = SofaTrRpcScanner.ANNOTATION)
    static class SofaTrScanConfiguration {

        @Bean
        public SofaTrRpcScanner archimedesSofaTrRpcScanner(ApplicationContext applicationContext) {
            return new SofaTrRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 tRPC（@TRpcService）时装配 tRPC 服务扫描（字符串条件，零 tRPC 编译依赖）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = TrpcRpcScanner.ANNOTATION)
    static class TrpcScanConfiguration {

        @Bean
        public TrpcRpcScanner archimedesTrpcRpcScanner(ApplicationContext applicationContext) {
            return new TrpcRpcScanner(applicationContext);
        }
    }
}
