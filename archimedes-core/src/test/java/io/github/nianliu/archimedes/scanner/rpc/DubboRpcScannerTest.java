package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
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

    @ApiModule(name = "定价", description = "定价服务")
    interface GreetingService {
        @ApiDoc(summary = "计算价格", description = "按数量计价")
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
    void fillsServiceAndMethodDescriptionFromAnnotations() {
        ServiceBean<?> serviceBean = mock(ServiceBean.class);
        doReturn(GreetingService.class.getName()).when(serviceBean).getInterface();
        doReturn(GreetingService.class).when(serviceBean).getInterfaceClass();
        doReturn("1.0.0").when(serviceBean).getVersion();
        doReturn("demo").when(serviceBean).getGroup();

        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("greetingServiceBean", serviceBean);
        context.refresh();

        RpcApiInfo svc = new DubboRpcScanner(context).contribute().get(0);
        // 服务级描述来自接口类 @ApiModule#description
        assertThat(svc.getDescription()).isEqualTo("定价服务");
        // 方法级描述来自 @ApiDoc#description（仅 greet 标注了注解）
        RpcMethodInfo priced = svc.getMethods().stream()
                .filter(m -> m.getDescription() != null)
                .findFirst().orElseThrow(AssertionError::new);
        assertThat(priced.getDescription()).isEqualTo("按数量计价");
    }

    @Test
    void emptyWhenNoServiceBeans() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        assertThat(new DubboRpcScanner(context).contribute()).isEmpty();
    }
}
