package io.github.nianliu.archimedes.boot3;

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

    // 纯静态配置容器，禁止实例化
    private RpcScanConfigurations() {
    }

    /** 宿主存在 Dubbo 时装配 provider 服务扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ServiceBean.class) // Dubbo 的 ServiceBean 在 classpath 才说明引入了 Dubbo
    static class DubboScanConfiguration {

        /** 从容器自省 Dubbo provider（ServiceBean）导出契约，无需依赖 Dubbo 运行时暴露的元数据。 */
        @Bean
        public DubboRpcScanner archimedesDubboRpcScanner(ApplicationContext applicationContext) {
            return new DubboRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 gRPC 时装配 BindableService 扫描。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.grpc.BindableService.class) // grpc 生成的服务实现均为 BindableService，据此判定 gRPC 存在
    static class GrpcScanConfiguration {

        /** 遍历容器内 BindableService Bean，反射其 ServiceDescriptor 得到方法级契约。 */
        @Bean
        public GrpcRpcScanner archimedesGrpcRpcScanner(ApplicationContext applicationContext) {
            return new GrpcRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 SOFABoot（@SofaService）时装配 SOFARPC 服务扫描（字符串条件，零 SOFA 编译依赖）。 */
    @Configuration(proxyBeanMethods = false)
    // 用注解全限定名的字符串形式判定，避免 starter 对 SOFA 产生编译期依赖
    @ConditionalOnClass(name = SofaTrRpcScanner.ANNOTATION)
    static class SofaTrScanConfiguration {

        /** 反射式扫描 @SofaService 标注的 provider，导出 SOFARPC-TR 契约。 */
        @Bean
        public SofaTrRpcScanner archimedesSofaTrRpcScanner(ApplicationContext applicationContext) {
            return new SofaTrRpcScanner(applicationContext);
        }
    }

    /** 宿主存在 tRPC（@TRpcService）时装配 tRPC 服务扫描（字符串条件，零 tRPC 编译依赖）。 */
    @Configuration(proxyBeanMethods = false)
    // 同样以字符串条件判定，避免对腾讯 tRPC 产生编译期依赖
    @ConditionalOnClass(name = TrpcRpcScanner.ANNOTATION)
    static class TrpcScanConfiguration {

        /** 反射式扫描 @TRpcService 标注的 provider，导出 tRPC 契约。 */
        @Bean
        public TrpcRpcScanner archimedesTrpcRpcScanner(ApplicationContext applicationContext) {
            return new TrpcRpcScanner(applicationContext);
        }
    }
}
