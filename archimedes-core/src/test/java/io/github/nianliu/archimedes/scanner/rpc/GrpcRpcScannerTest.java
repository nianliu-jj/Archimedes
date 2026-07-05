package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcRpcScannerTest {

    /** protobuf 系 marshaller 的最小仿真：实现 PrototypeMarshaller 以驱动消息类型解析分支。 */
    static class StringPrototypeMarshaller implements MethodDescriptor.PrototypeMarshaller<String> {
        @Override
        public String getMessagePrototype() {
            return "";
        }

        @Override
        public Class<String> getMessageClass() {
            return String.class;
        }

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return new Scanner(stream, "UTF-8").useDelimiter("\\A").next();
        }
    }

    static final ServerCallHandler<String, String> NOOP_HANDLER = new ServerCallHandler<String, String>() {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            return new ServerCall.Listener<String>() {
            };
        }
    };

    static class ManualGreeterService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            MethodDescriptor<String, String> sayHello = MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("demo.Greeter/SayHello")
                    .setRequestMarshaller(new StringPrototypeMarshaller())
                    .setResponseMarshaller(new StringPrototypeMarshaller())
                    .build();
            MethodDescriptor<String, String> streamHello = MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("demo.Greeter/StreamHello")
                    .setRequestMarshaller(new StringPrototypeMarshaller())
                    .setResponseMarshaller(new StringPrototypeMarshaller())
                    .build();
            return ServerServiceDefinition.builder("demo.Greeter")
                    .addMethod(sayHello, NOOP_HANDLER)
                    .addMethod(streamHello, NOOP_HANDLER)
                    .build();
        }
    }

    @Test
    void extractsServiceContractFromBindableService() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("greeter", new ManualGreeterService());
        context.refresh();

        List<RpcApiInfo> result = new GrpcRpcScanner(context).contribute();

        assertThat(result).hasSize(1);
        RpcApiInfo api = result.get(0);
        assertThat(api.getProtocol()).isEqualTo("GRPC");
        assertThat(api.getServiceName()).isEqualTo("demo.Greeter");
        assertThat(api.getMethods()).hasSize(2);

        RpcMethodInfo sayHello = api.getMethods().get(0);
        assertThat(sayHello.getMethodName()).isEqualTo("SayHello");
        assertThat(sayHello.getMetadata()).containsEntry("grpcMethodType", "UNARY");
        assertThat(sayHello.getParameterTypes()).containsExactly("java.lang.String");
        assertThat(sayHello.getReturnType()).isEqualTo("java.lang.String");

        RpcMethodInfo streamHello = api.getMethods().get(1);
        assertThat(streamHello.getMethodName()).isEqualTo("StreamHello");
        assertThat(streamHello.getMetadata()).containsEntry("grpcMethodType", "SERVER_STREAMING");
    }

    @Test
    void emptyWhenNoBindableServices() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.refresh();

        assertThat(new GrpcRpcScanner(context).contribute()).isEmpty();
    }
}
