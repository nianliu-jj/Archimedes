package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOFARPC（TR 协议体系）服务扫描：反射读取 @SofaService 注解 Bean，
 * 提取 interfaceType/uniqueId/bindings；零 SOFA 编译依赖。
 */
public class SofaTrRpcScanner extends AnnotatedRpcScannerSupport {

    public static final String ANNOTATION = "com.alipay.sofa.runtime.api.annotation.SofaService";

    public SofaTrRpcScanner(ApplicationContext applicationContext) {
        super(applicationContext, ANNOTATION, RpcApiInfo.PROTOCOL_SOFA_TR);
    }

    @Override
    protected String interfaceAttributeName() {
        return "interfaceType";
    }

    @Override
    protected RpcApiInfo build(AnnotationAttributes attributes, ServiceTarget target,
                               List<RpcMethodInfo> methods) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (attributes.containsKey("uniqueId")) {
            String uniqueId = String.valueOf(attributes.get("uniqueId"));
            if (StringUtils.hasText(uniqueId)) {
                metadata.put("uniqueId", uniqueId);
            }
        }
        String bindings = bindingTypes(attributes);
        if (StringUtils.hasText(bindings)) {
            metadata.put("bindings", bindings);
        }
        if (target.isFromImplementation()) {
            metadata.put("serviceNameSource", "implementationClass");
        }
        return new RpcApiInfo(RpcApiInfo.PROTOCOL_SOFA_TR, target.getType().getName(),
                null, null, methods, metadata.isEmpty() ? null : metadata);
    }

    private String bindingTypes(AnnotationAttributes attributes) {
        Object bindings = attributes.get("bindings");
        if (!(bindings instanceof AnnotationAttributes[])) {
            return null;
        }
        List<String> types = new ArrayList<>();
        for (AnnotationAttributes binding : (AnnotationAttributes[]) bindings) {
            Object type = binding.get("bindingType");
            if (type != null && StringUtils.hasText(String.valueOf(type))) {
                types.add(String.valueOf(type));
            }
        }
        return types.isEmpty() ? null : StringUtils.collectionToCommaDelimitedString(types);
    }
}
