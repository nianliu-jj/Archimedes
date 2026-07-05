package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描容器中的 gRPC BindableService Bean（@GrpcService 等集成注册的服务实现均为此类型），
 * 经 bindService() 读取 ServerServiceDefinition 提取契约。不要求 Server Reflection 或 gRPC Server 启动。
 */
public class GrpcRpcScanner implements RpcApiContributor {

    static final String METADATA_METHOD_TYPE = "grpcMethodType";

    private final ApplicationContext applicationContext;

    public GrpcRpcScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<RpcApiInfo> contribute() {
        List<RpcApiInfo> result = new ArrayList<>();
        Map<String, BindableService> beans = applicationContext.getBeansOfType(BindableService.class);
        for (BindableService service : beans.values()) {
            ServerServiceDefinition definition = service.bindService();
            result.add(describe(definition));
        }
        result.sort(Comparator.comparing(RpcApiInfo::getServiceName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private RpcApiInfo describe(ServerServiceDefinition definition) {
        List<RpcMethodInfo> methods = new ArrayList<>();
        for (ServerMethodDefinition<?, ?> methodDef : definition.getMethods()) {
            MethodDescriptor<?, ?> descriptor = methodDef.getMethodDescriptor();
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(METADATA_METHOD_TYPE, descriptor.getType().name());

            String requestType = prototypeClassName(descriptor.getRequestMarshaller());
            String responseType = prototypeClassName(descriptor.getResponseMarshaller());
            List<String> parameterTypes = requestType == null
                    ? Collections.<String>emptyList()
                    : Collections.singletonList(requestType);

            methods.add(new RpcMethodInfo(bareMethodName(descriptor), parameterTypes, responseType, metadata));
        }
        methods.sort(Comparator.comparing(RpcMethodInfo::getMethodName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new RpcApiInfo(RpcApiInfo.PROTOCOL_GRPC,
                definition.getServiceDescriptor().getName(), null, null, methods);
    }

    private String bareMethodName(MethodDescriptor<?, ?> descriptor) {
        String bare = descriptor.getBareMethodName();
        if (bare != null) {
            return bare;
        }
        String full = descriptor.getFullMethodName();
        int slash = full.lastIndexOf('/');
        return slash >= 0 ? full.substring(slash + 1) : full;
    }

    /** protobuf 系 marshaller 均实现 PrototypeMarshaller，可取消息原型类名；其它 marshaller 返回 null。 */
    private String prototypeClassName(MethodDescriptor.Marshaller<?> marshaller) {
        if (marshaller instanceof MethodDescriptor.PrototypeMarshaller) {
            Object prototype = ((MethodDescriptor.PrototypeMarshaller<?>) marshaller).getMessagePrototype();
            if (prototype != null) {
                return prototype.getClass().getName();
            }
        }
        return null;
    }
}
