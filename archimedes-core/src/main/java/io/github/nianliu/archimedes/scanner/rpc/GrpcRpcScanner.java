package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class GrpcRpcScanner implements RpcApiContributor {

    /** 方法元数据键：记录 gRPC 方法调用类型（UNARY/SERVER_STREAMING 等）。 */
    static final String METADATA_METHOD_TYPE = "grpcMethodType";

    private final ApplicationContext applicationContext;

    public GrpcRpcScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 取容器中全部 BindableService，绑定后描述其服务定义，按服务名排序输出。 */
    @Override
    public List<RpcApiInfo> contribute() {
        List<RpcApiInfo> result = new ArrayList<>();
        Map<String, BindableService> beans = applicationContext.getBeansOfType(BindableService.class);
        for (BindableService service : beans.values()) {
            // bindService() 是获取方法定义的标准入口，无需真正启动 gRPC Server
            ServerServiceDefinition definition = service.bindService();
            RpcApiInfo api = describe(definition);
            // 服务级描述：从 BindableService 实现类上的 @ApiModule#description 读取（空串归 null）。
            // 方法级描述不做：gRPC 方法名为 protobuf 名（如 SayHello），无法可靠映射回带注解的 Java 方法。
            api.setDescription(TypeSchemaResolver.tagDescriptionOrNull(service.getClass().getAnnotations()));
            result.add(api);
        }
        result.sort(Comparator.comparing(RpcApiInfo::getServiceName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** 遍历服务定义中的方法：记录调用类型元数据，解析请求/响应消息原型类名作为参数与返回类型。 */
    private RpcApiInfo describe(ServerServiceDefinition definition) {
        List<RpcMethodInfo> methods = new ArrayList<>();
        for (ServerMethodDefinition<?, ?> methodDef : definition.getMethods()) {
            MethodDescriptor<?, ?> descriptor = methodDef.getMethodDescriptor();
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(METADATA_METHOD_TYPE, descriptor.getType().name());

            String requestType = prototypeClassName(descriptor.getRequestMarshaller());
            String responseType = prototypeClassName(descriptor.getResponseMarshaller());
            // gRPC 方法固定单请求消息，取不到原型时以空参数列表表示
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

    /** 取裸方法名；老版本 descriptor 无 bareMethodName 时从全限定名（service/method）截取斜杠后段。 */
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
