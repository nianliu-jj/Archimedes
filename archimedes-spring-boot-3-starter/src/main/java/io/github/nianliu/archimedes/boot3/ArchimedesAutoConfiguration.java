package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.scanner.rpc.RpcApiContributor;
import io.github.nianliu.archimedes.scanner.ws.SpringWebSocketHandlerScanner;
import io.github.nianliu.archimedes.scanner.ws.StompMappingScanner;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SERVLET（Spring MVC）分支的核心自动装配：注册 REST 契约扫描器与内置 API 控制器，
 * 并按需 {@code @Import} 各协议扫描子配置（RPC 四协议 + WebSocket 三形态）。
 * 采用 Spring Boot 2.7+ 的 {@code @AutoConfiguration}（走 AutoConfiguration.imports 注册），
 * 与 REACTIVE 分支（{@link ArchimedesReactiveAutoConfiguration}）条件互斥、两者不会同时生效。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
// afterName：在 WebMvcAutoConfiguration 之后装配，确保 RequestMappingHandlerMapping 等已就绪可被注入扫描
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
// 仅在 Servlet 型 Web 应用生效，与响应式分支互斥
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 宿主 classpath 存在 Spring MVC 的 RequestMappingHandlerMapping 才装配（判定当前是 MVC 而非纯响应式栈）
@ConditionalOnClass(RequestMappingHandlerMapping.class)
// 允许通过 archimedes.api.enabled=false 全局关闭；缺省视为开启（引入即用）
@ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ArchimedesApiProperties.class)
// 导入栈无关的 RPC 四协议扫描子配置，各自再按 @ConditionalOnClass 决定是否真正生效
@Import({RpcScanConfigurations.DubboScanConfiguration.class,
        RpcScanConfigurations.GrpcScanConfiguration.class,
        RpcScanConfigurations.SofaTrScanConfiguration.class,
        RpcScanConfigurations.TrpcScanConfiguration.class})
public class ArchimedesAutoConfiguration {

    /** REST 契约扫描器：注入全部 RequestMappingHandlerMapping，自省出所有 REST 端点契约。 */
    @Bean
    public RestApiScanner archimedesRestApiScanner(List<RequestMappingHandlerMapping> handlerMappings,
                                                   ArchimedesApiProperties properties) {
        return new RestApiScanner(handlerMappings, properties);
    }

    /**
     * 内置 API 控制器（契约展示/在线调试入口）：聚合 REST 扫描结果与各协议贡献者，
     * WebSocket/RPC 贡献者按 classpath 条件可能缺省，故用 ObjectProvider 按序收集、允许为空。
     */
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
    @ConditionalOnClass(WebSocketHandler.class) // classpath 有 WebSocketHandler 才说明引入了 spring-websocket
    static class WebSocketHandlerScanConfiguration {

        /** 从 SimpleUrlHandlerMapping 反查 WebSocket handler 与其 URL 映射；宿主未配置 WS 时该 mapping 缺省，故用 ObjectProvider。 */
        @Bean
        public SpringWebSocketHandlerScanner archimedesWebSocketHandlerScanner(
                ObjectProvider<SimpleUrlHandlerMapping> handlerMappings) {
            return new SpringWebSocketHandlerScanner(
                    handlerMappings.orderedStream().collect(Collectors.toList()));
        }
    }

    /** 宿主存在 spring-messaging 时装配 STOMP 注解映射扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SimpAnnotationMethodMessageHandler.class) // classpath 有该处理器才说明启用了 STOMP 注解模型
    static class StompScanConfiguration {

        /** 从 SimpAnnotationMethodMessageHandler 反查 @MessageMapping/@SubscribeMapping 端点；未启用 STOMP 时缺省。 */
        @Bean
        public StompMappingScanner archimedesStompMappingScanner(
                ObjectProvider<SimpAnnotationMethodMessageHandler> messageHandlers) {
            return new StompMappingScanner(
                    messageHandlers.orderedStream().collect(Collectors.toList()));
        }
    }

    /** 宿主存在 jakarta.websocket API 时装配 @ServerEndpoint 扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(jakarta.websocket.server.ServerEndpoint.class) // SB3 用 jakarta 命名空间，据此判定标准 WebSocket API 存在
    static class ServerEndpointScanConfiguration {

        /** 扫描注册为 Spring Bean 的 jakarta @ServerEndpoint 端点，交由 Boot3ServerEndpointScanner 实现。 */
        @Bean
        public Boot3ServerEndpointScanner archimedesServerEndpointScanner(ApplicationContext applicationContext) {
            return new Boot3ServerEndpointScanner(applicationContext);
        }
    }
}
