package io.github.nianliu.archimedes.scanner.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import com.tencent.trpc.spring.annotation.TRpcService;
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedRpcScannersTest {

    // 服务接口标注自有注解：@ApiModule 提供服务级描述、@ApiDoc 提供方法级描述。
    // SOFA（显式 interfaceType）与 tRPC（唯一接口兜底）都会解析到该接口，故一处标注覆盖两协议。
    @ApiModule(description = "问候服务")
    interface GreetingService {
        @ApiDoc(description = "打招呼")
        String greet(String name);
    }

    @SofaService(interfaceType = GreetingService.class, uniqueId = "demo",
            bindings = {@SofaServiceBinding(bindingType = "tr"), @SofaServiceBinding(bindingType = "bolt")})
    static class SofaGreetingImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hi " + name;
        }
    }

    @TRpcService(name = "demo.trpc.Greeting", version = "v1", group = "g1")
    static class TrpcGreetingImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hi " + name;
        }
    }

    private StaticApplicationContext contextWith(Object... beans) {
        StaticApplicationContext context = new StaticApplicationContext();
        int i = 0;
        for (Object bean : beans) {
            context.getBeanFactory().registerSingleton("bean" + (i++), bean);
        }
        context.refresh();
        return context;
    }

    @Test
    void sofaScannerExtractsInterfaceUniqueIdAndBindings() {
        List<RpcApiInfo> result = new SofaTrRpcScanner(contextWith(new SofaGreetingImpl())).contribute();

        assertThat(result).hasSize(1);
        RpcApiInfo api = result.get(0);
        assertThat(api.getProtocol()).isEqualTo("SOFA_TR");
        assertThat(api.getServiceName()).isEqualTo(GreetingService.class.getName());
        assertThat(api.getMetadata()).containsEntry("uniqueId", "demo")
                .containsEntry("bindings", "tr,bolt");
        assertThat(api.getMethods()).hasSize(1);
        assertThat(api.getMethods().get(0).getMethodName()).isEqualTo("greet");
        // 服务级描述来自接口上的 @ApiModule#description
        assertThat(api.getDescription()).isEqualTo("问候服务");
        // 方法级描述来自 greet 上的 @ApiDoc#description
        RpcMethodInfo greet = api.getMethods().get(0);
        assertThat(greet.getDescription()).isEqualTo("打招呼");
    }

    @Test
    void trpcScannerExtractsAttributesAndFallsBackToSoleInterface() {
        List<RpcApiInfo> result = new TrpcRpcScanner(contextWith(new TrpcGreetingImpl())).contribute();

        assertThat(result).hasSize(1);
        RpcApiInfo api = result.get(0);
        assertThat(api.getProtocol()).isEqualTo("TRPC");
        assertThat(api.getServiceName()).isEqualTo(GreetingService.class.getName());
        assertThat(api.getVersion()).isEqualTo("v1");
        assertThat(api.getGroup()).isEqualTo("g1");
        assertThat(api.getMetadata()).containsEntry("name", "demo.trpc.Greeting");
        // 同一接口标注对 tRPC（唯一接口兜底）同样生效
        assertThat(api.getDescription()).isEqualTo("问候服务");
        assertThat(api.getMethods()).hasSize(1);
        assertThat(api.getMethods().get(0).getDescription()).isEqualTo("打招呼");
    }

    @Test
    void emptyWhenNoAnnotatedBeans() {
        assertThat(new SofaTrRpcScanner(contextWith()).contribute()).isEmpty();
        assertThat(new TrpcRpcScanner(contextWith()).contribute()).isEmpty();
    }
}
