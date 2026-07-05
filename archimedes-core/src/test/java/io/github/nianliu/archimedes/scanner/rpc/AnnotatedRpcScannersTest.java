package io.github.nianliu.archimedes.scanner.rpc;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import com.tencent.trpc.spring.annotation.TRpcService;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedRpcScannersTest {

    interface GreetingService {
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
    }

    @Test
    void emptyWhenNoAnnotatedBeans() {
        assertThat(new SofaTrRpcScanner(contextWith()).contribute()).isEmpty();
        assertThat(new TrpcRpcScanner(contextWith()).contribute()).isEmpty();
    }
}
