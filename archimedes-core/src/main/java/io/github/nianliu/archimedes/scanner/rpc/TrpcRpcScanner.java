package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯 tRPC 服务扫描：反射读取 @TRpcService 注解 Bean（FQCN 按开源 tRPC-Java 的 Spring
 * 集成公开形态假设，不命中即不装配），防御式读取 name/version/group；零 tRPC 编译依赖。
 */
public class TrpcRpcScanner extends AnnotatedRpcScannerSupport {

    public static final String ANNOTATION = "com.tencent.trpc.spring.annotation.TRpcService";

    public TrpcRpcScanner(ApplicationContext applicationContext) {
        super(applicationContext, ANNOTATION, RpcApiInfo.PROTOCOL_TRPC);
    }

    @Override
    protected RpcApiInfo build(AnnotationAttributes attributes, ServiceTarget target,
                               List<RpcMethodInfo> methods) {
        Map<String, String> metadata = new LinkedHashMap<>();
        String version = stringAttribute(attributes, "version");
        String group = stringAttribute(attributes, "group");
        String name = stringAttribute(attributes, "name");
        if (name != null) {
            metadata.put("name", name);
        }
        if (target.isFromImplementation()) {
            metadata.put("serviceNameSource", "implementationClass");
        }
        return new RpcApiInfo(RpcApiInfo.PROTOCOL_TRPC, target.getType().getName(),
                version, group, methods, metadata.isEmpty() ? null : metadata);
    }

    private String stringAttribute(AnnotationAttributes attributes, String name) {
        if (!attributes.containsKey(name)) {
            return null;
        }
        Object value = attributes.get(name);
        if (value instanceof String && StringUtils.hasText((String) value)) {
            return (String) value;
        }
        return null;
    }
}
