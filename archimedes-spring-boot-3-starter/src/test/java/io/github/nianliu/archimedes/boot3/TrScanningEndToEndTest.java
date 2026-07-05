package io.github.nianliu.archimedes.boot3;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.trpc.spring.annotation.TRpcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** SOFARPC-TR 与 tRPC 注解服务的端到端扫描验证（桩注解驱动真实反射路径）。 */
@SpringBootTest(classes = TrScanningEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrScanningEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rpcApis() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        return (List<Map<String, Object>>) catalog.get("rpcApis");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scansSofaTrService() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("SOFA_TR");
            assertThat(api.get("serviceName")).isEqualTo(GreetingService.class.getName());
            Map<String, Object> metadata = (Map<String, Object>) api.get("metadata");
            assertThat(metadata.get("uniqueId")).isEqualTo("demo");
            assertThat(metadata.get("bindings")).isEqualTo("tr");
        });
    }

    @Test
    void scansTrpcService() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("TRPC");
            assertThat(api.get("serviceName")).isEqualTo(EchoService.class.getName());
            assertThat(api.get("version")).isEqualTo("v1");
        });
    }

    public interface GreetingService {
        String greet(String name);
    }

    public interface EchoService {
        String echo(String message);
    }

    @SofaService(interfaceType = GreetingService.class, uniqueId = "demo",
            bindings = @SofaServiceBinding(bindingType = "tr"))
    public static class SofaGreetingImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hi " + name;
        }
    }

    @TRpcService(name = "demo.Echo", version = "v1")
    public static class TrpcEchoImpl implements EchoService {
        @Override
        public String echo(String message) {
            return message;
        }
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        SofaGreetingImpl sofaGreeting() {
            return new SofaGreetingImpl();
        }

        @Bean
        TrpcEchoImpl trpcEcho() {
            return new TrpcEchoImpl();
        }
    }
}
