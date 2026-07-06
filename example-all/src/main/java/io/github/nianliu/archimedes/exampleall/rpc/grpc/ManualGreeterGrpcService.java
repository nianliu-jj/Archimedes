package io.github.nianliu.archimedes.exampleall.rpc.grpc;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * gRPC 契约演示服务：手写 ServerServiceDefinition（零 protoc/零 stub 生成），
 * 只要容器里存在 BindableService Bean，Archimedes 即自省其 bindService() 提取契约——
 * 期望契约：protocol=GRPC、serviceName=demo.Greeter，两个方法分别为
 * UNARY（SayHello）与 SERVER_STREAMING（StreamGreetings，验证 streaming 形态 metadata）。
 * 注意：无需真的启动 gRPC Server，契约扫描只读取服务定义。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Component
public class ManualGreeterGrpcService implements BindableService {

    /** 契约中的 gRPC 服务名（package.Service 形态） */
    public static final String SERVICE_NAME = "demo.Greeter";

    @Override
    public ServerServiceDefinition bindService() {
        // 一元方法：SayHello(String) → String
        MethodDescriptor<String, String> sayHello = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(SERVICE_NAME + "/SayHello")
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
        // 服务端流方法：验证契约 metadata 中的 grpcMethodType=SERVER_STREAMING
        MethodDescriptor<String, String> streamGreetings = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                .setFullMethodName(SERVICE_NAME + "/StreamGreetings")
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
        ServerCallHandler<String, String> handler = noopHandler();
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(sayHello, handler)
                .addMethod(streamGreetings, handler)
                .build();
    }

    /** 契约演示不处理真实调用：空监听器即可 */
    private static ServerCallHandler<String, String> noopHandler() {
        return new ServerCallHandler<String, String>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                return new ServerCall.Listener<String>() {
                };
            }
        };
    }

    /** String 直通编解码器（演示用，替代 protobuf 生成物） */
    static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return new Scanner(stream, StandardCharsets.UTF_8).useDelimiter("\\A").next();
        }
    }
}
