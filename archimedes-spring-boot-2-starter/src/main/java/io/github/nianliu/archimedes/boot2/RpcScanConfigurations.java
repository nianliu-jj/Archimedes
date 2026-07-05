package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.scanner.rpc.DubboRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.GrpcRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.SofaTrRpcScanner;
import io.github.nianliu.archimedes.scanner.rpc.TrpcRpcScanner;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 四类 RPC 扫描器的共享条件配置：容器自省实现、与 Web 栈无关，
 * 由 SERVLET（{@link ArchimedesAutoConfiguration}）与 REACTIVE
 * （{@link ArchimedesReactiveAutoConfiguration}）两个自动装配分支
 * {@code @Import} 复用——两分支条件互斥，运行时不会重复注册。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
final class RpcScanConfigurations {

    private RpcScanConfigurations() {
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
