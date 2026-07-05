package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.ReactiveRestApiScanner;
import io.github.nianliu.archimedes.scanner.rpc.RpcApiContributor;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REACTIVE（WebFlux）分支自动装配（Spring Boot 2.7.x 侧）：注册响应式 REST
 * 扫描器并复用纯注解式的 ArchimedesApiController。与 SERVLET 分支条件互斥；
 * RPC 四协议扫描（栈无关）经共享配置同等装配；WebSocket 扫描依赖 servlet 栈，
 * 仅存在于 SERVLET 分支。
 * 注意：链路追踪与日志采集当前仅 Servlet 栈（响应式需 Reactor Context 传播，后续版本支持）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(WebFluxAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ArchimedesApiProperties.class)
@Import({RpcScanConfigurations.DubboScanConfiguration.class,
        RpcScanConfigurations.GrpcScanConfiguration.class,
        RpcScanConfigurations.SofaTrScanConfiguration.class,
        RpcScanConfigurations.TrpcScanConfiguration.class})
public class ArchimedesReactiveAutoConfiguration {

    @Bean
    public ReactiveRestApiScanner archimedesReactiveRestApiScanner(
            List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        return new ReactiveRestApiScanner(handlerMappings, properties);
    }

    @Bean
    public ArchimedesApiController archimedesApiController(ReactiveRestApiScanner scanner,
                                                           ArchimedesApiProperties properties,
                                                           ObjectProvider<WebSocketApiContributor> contributors,
                                                           ObjectProvider<RpcApiContributor> rpcContributors) {
        return new ArchimedesApiController(scanner, properties,
                contributors.orderedStream().collect(Collectors.toList()),
                rpcContributors.orderedStream().collect(Collectors.toList()));
    }
}
