package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import org.apache.dubbo.config.spring.ServiceBean;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class DubboRpcScannerTest {

    interface GreetingService {
        String greet(String name);

        int add(int a, int b);
    }

    @Test
    void extractsServiceContractFromServiceBean() {
        // Dubbo 3.2 的 ServiceBean 无公共无参构造，mock 其读取面
        ServiceBean<?> serviceBean = mock(ServiceBean.class);
        doReturn(GreetingService.class.getName()).when(serviceBean).getInterface();
        doReturn(GreetingService.class).when(serviceBean).getInterfaceClass();
        doReturn("1.0.0").when(serviceBean).getVersion();
        doReturn("demo").when(serviceBean).getGroup();

        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("greetingServiceBean", serviceBean);
        context.refresh();

        List<RpcApiInfo> result = new DubboRpcScanner(context).contribute();

        assertThat(result).hasSize(1);
        RpcApiInfo api = result.get(0);
        assertThat(api.getProtocol()).isEqualTo("DUBBO");
        assertThat(api.getServiceName()).isEqualTo(GreetingService.class.getName());
        assertThat(api.getVersion()).isEqualTo("1.0.0");
        assertThat(api.getGroup()).isEqualTo("demo");
        assertThat(api.getMethods()).hasSize(2);

        RpcMethodInfo add = api.getMethods().get(0);
        assertThat(add.getMethodName()).isEqualTo("add");
        assertThat(add.getParameterTypes()).containsExactly("int", "int");
        assertThat(add.getReturnType()).isEqualTo("int");

        RpcMethodInfo greet = api.getMethods().get(1);
        assertThat(greet.getMethodName()).isEqualTo("greet");
        assertThat(greet.getParameterTypes()).containsExactly("java.lang.String");
        assertThat(greet.getReturnType()).isEqualTo("java.lang.String");
    }

    @Test
    void emptyWhenNoServiceBeans() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        assertThat(new DubboRpcScanner(context).contribute()).isEmpty();
    }
}
