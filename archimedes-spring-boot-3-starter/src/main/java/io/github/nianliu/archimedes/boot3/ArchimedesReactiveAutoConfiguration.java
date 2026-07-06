package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.ReactiveRestApiScanner;
import io.github.nianliu.archimedes.scanner.rpc.RpcApiContributor;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REACTIVE（WebFlux）分支自动装配：注册响应式 REST 扫描器并复用纯注解式的
 * ArchimedesApiController（该控制器零 servlet 依赖，WebFlux 注解模型原样支持）。
 * 与 SERVLET 分支条件互斥；RPC 四协议扫描（栈无关）经共享配置同等装配；
 * WebSocket 扫描依赖 servlet 栈，仅存在于 SERVLET 分支。
 * 注意：链路追踪与日志采集当前仅 Servlet 栈（响应式需 Reactor Context 传播，后续版本支持）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
// afterName：在 WebFluxAutoConfiguration 之后装配，确保响应式 RequestMappingHandlerMapping 已就绪
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration")
// 仅在 REACTIVE 型 Web 应用生效，与 SERVLET 分支互斥
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
// 注意：此处的 RequestMappingHandlerMapping 是 web.reactive 包下的，据此判定当前为 WebFlux 栈
@ConditionalOnClass(RequestMappingHandlerMapping.class)
// 允许通过 archimedes.api.enabled=false 全局关闭；缺省视为开启
@ConditionalOnProperty(prefix = "archimedes.api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ArchimedesApiProperties.class)
// 复用与 SERVLET 分支相同的 RPC 四协议扫描子配置（栈无关）
@Import({RpcScanConfigurations.DubboScanConfiguration.class,
        RpcScanConfigurations.GrpcScanConfiguration.class,
        RpcScanConfigurations.SofaTrScanConfiguration.class,
        RpcScanConfigurations.TrpcScanConfiguration.class})
public class ArchimedesReactiveAutoConfiguration {

    /** 响应式 REST 契约扫描器：注入 WebFlux 的 RequestMappingHandlerMapping，自省响应式端点契约。 */
    @Bean
    public ReactiveRestApiScanner archimedesReactiveRestApiScanner(
            List<RequestMappingHandlerMapping> handlerMappings, ArchimedesApiProperties properties) {
        return new ReactiveRestApiScanner(handlerMappings, properties);
    }

    /** 内置 API 控制器：与 SERVLET 分支同一个纯注解式控制器，仅 REST 扫描器换成响应式实现；RPC 贡献者按序收集、可空。 */
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
