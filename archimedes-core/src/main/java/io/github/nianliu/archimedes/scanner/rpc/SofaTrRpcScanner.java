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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class SofaTrRpcScanner extends AnnotatedRpcScannerSupport {

    /** @SofaService 注解 FQCN，classpath 不存在时本扫描器静默不装配。 */
    public static final String ANNOTATION = "com.alipay.sofa.runtime.api.annotation.SofaService";

    public SofaTrRpcScanner(ApplicationContext applicationContext) {
        super(applicationContext, ANNOTATION, RpcApiInfo.PROTOCOL_SOFA_TR);
    }

    /** @SofaService 用 interfaceType 属性声明服务接口。 */
    @Override
    protected String interfaceAttributeName() {
        return "interfaceType";
    }

    /** 将 uniqueId、绑定协议、兜底来源整理进 metadata，组装 SOFA-TR 协议的 RpcApiInfo。 */
    @Override
    protected RpcApiInfo build(AnnotationAttributes attributes, ServiceTarget target,
                               List<RpcMethodInfo> methods) {
        Map<String, String> metadata = new LinkedHashMap<>();
        // uniqueId 用于区分同接口的多实例，有值才记录
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
        // 服务名来自实现类兜底（非接口）时标注来源，提示契约精度
        if (target.isFromImplementation()) {
            metadata.put("serviceNameSource", "implementationClass");
        }
        return new RpcApiInfo(RpcApiInfo.PROTOCOL_SOFA_TR, target.getType().getName(),
                null, null, methods, metadata.isEmpty() ? null : metadata);
    }

    /** 从 @SofaService 的 bindings 数组（嵌套 @SofaServiceBinding）提取各 bindingType，逗号拼接。 */
    private String bindingTypes(AnnotationAttributes attributes) {
        Object bindings = attributes.get("bindings");
        // 父类以 nestedAnnotationsAsMap=true 读取，嵌套注解呈现为 AnnotationAttributes[]
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
