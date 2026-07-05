package io.github.nianliu.archimedes.boot3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内嵌 Dubbo provider（registry N/A 本地导出、QoS 关闭、随机协议端口）验证扫描输出。
 */
@SpringBootTest(classes = DubboScanningEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dubbo.enabled=true",
                "dubbo.application.name=archimedes-dubbo-test",
                "dubbo.registry.address=N/A",
                "dubbo.application.qos-enable=false",
                "dubbo.protocol.name=dubbo",
                "dubbo.protocol.port=-1"
        })
class DubboScanningEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void scansDubboProviderContract() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> rpcApis = (List<Map<String, Object>>) catalog.get("rpcApis");

        assertThat(rpcApis).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("DUBBO");
            assertThat(api.get("serviceName")).isEqualTo(GreetingService.class.getName());
            assertThat(api.get("version")).isEqualTo("1.0.0");
            assertThat(api.get("group")).isEqualTo("demo");
            List<Map<String, Object>> methods = (List<Map<String, Object>>) api.get("methods");
            assertThat(methods).anySatisfy(m -> {
                assertThat(m.get("methodName")).isEqualTo("greet");
                assertThat((List<String>) m.get("parameterTypes")).containsExactly("java.lang.String");
                assertThat(m.get("returnType")).isEqualTo("java.lang.String");
            });
        });
    }

    public interface GreetingService {
        String greet(String name);
    }

    @DubboService(version = "1.0.0", group = "demo")
    public static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet(String name) {
            return "hi " + name;
        }
    }

    @EnableDubbo(scanBasePackages = "io.github.nianliu.archimedes.boot3")
    @EnableAutoConfiguration
    @Configuration
    static class TestApp {
    }
}
