package io.github.nianliu.archimedes.boot3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

/** gRPC BindableService Bean 的端到端扫描验证（手写服务定义，零 protoc）。 */
@SpringBootTest(classes = GrpcScanningEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GrpcScanningEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void scansGrpcServiceContract() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        List<Map<String, Object>> rpcApis = (List<Map<String, Object>>) catalog.get("rpcApis");

        assertThat(rpcApis).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("GRPC");
            assertThat(api.get("serviceName")).isEqualTo("demo.Greeter");
            List<Map<String, Object>> methods = (List<Map<String, Object>>) api.get("methods");
            assertThat(methods).anySatisfy(m -> {
                assertThat(m.get("methodName")).isEqualTo("SayHello");
                Map<String, Object> metadata = (Map<String, Object>) m.get("metadata");
                assertThat(metadata.get("grpcMethodType")).isEqualTo("UNARY");
            });
        });
    }

    static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return new Scanner(stream, "UTF-8").useDelimiter("\\A").next();
        }
    }

    static class ManualGreeterService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            MethodDescriptor<String, String> sayHello = MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("demo.Greeter/SayHello")
                    .setRequestMarshaller(new StringMarshaller())
                    .setResponseMarshaller(new StringMarshaller())
                    .build();
            return ServerServiceDefinition.builder("demo.Greeter")
                    .addMethod(sayHello, new ServerCallHandler<String, String>() {
                        @Override
                        public ServerCall.Listener<String> startCall(ServerCall<String, String> call,
                                                                     Metadata headers) {
                            return new ServerCall.Listener<String>() {
                            };
                        }
                    })
                    .build();
        }
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        ManualGreeterService greeterService() {
            return new ManualGreeterService();
        }
    }
}
