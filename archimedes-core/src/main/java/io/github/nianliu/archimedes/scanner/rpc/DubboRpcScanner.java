package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描容器中的 Dubbo ServiceBean（@DubboService 注解与 XML 注册最终都产生它），
 * 提取接口全限定名、version、group 与接口业务方法签名。
 * 所用 API（getInterface/getInterfaceClass/getVersion/getGroup）在 Dubbo 2.7 与 3.x 一致。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class DubboRpcScanner implements RpcApiContributor {

    private final ApplicationContext applicationContext;

    public DubboRpcScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 取容器中全部 ServiceBean 逐个描述，按服务名排序输出。 */
    @Override
    public List<RpcApiInfo> contribute() {
        List<RpcApiInfo> result = new ArrayList<>();
        Map<String, ServiceBean> beans = applicationContext.getBeansOfType(ServiceBean.class);
        for (ServiceBean<?> serviceBean : beans.values()) {
            result.add(describe(serviceBean));
        }
        result.sort(Comparator.comparing(RpcApiInfo::getServiceName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** 从 ServiceBean 提取接口方法签名与 version/group；interfaceClass 缺失时方法列表为空。 */
    private RpcApiInfo describe(ServiceBean<?> serviceBean) {
        List<RpcMethodInfo> methods = new ArrayList<>();
        Class<?> interfaceClass = serviceBean.getInterfaceClass();
        if (interfaceClass != null) {
            for (Method method : interfaceClass.getMethods()) {
                // 排除 Object 自带方法，仅保留业务接口方法
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                List<String> parameterTypes = new ArrayList<>();
                for (Class<?> parameterType : method.getParameterTypes()) {
                    parameterTypes.add(parameterType.getName());
                }
                RpcMethodInfo mi = new RpcMethodInfo(method.getName(), parameterTypes,
                        method.getReturnType().getName());
                // 方法级描述：读该方法的 @ApiDoc，description 优先、回退 summary、再回退 value；空串归 null
                ApiDoc doc = method.getAnnotation(ApiDoc.class);
                if (doc != null) {
                    String text = !doc.description().isEmpty() ? doc.description()
                            : (!doc.summary().isEmpty() ? doc.summary() : doc.value());
                    mi.setDescription(text.isEmpty() ? null : text);
                }
                methods.add(mi);
            }
            methods.sort(Comparator.comparing(RpcMethodInfo::getMethodName));
        }
        // getInterface() 返回接口 FQCN 字符串（可能来自 XML 配置），与 getInterfaceClass() 互补
        RpcApiInfo api = new RpcApiInfo(RpcApiInfo.PROTOCOL_DUBBO,
                serviceBean.getInterface(), serviceBean.getVersion(), serviceBean.getGroup(), methods);
        // 服务级描述：读接口类的 @ApiModule#description，空串归 null
        if (interfaceClass != null) {
            api.setDescription(TypeSchemaResolver.tagDescriptionOrNull(interfaceClass.getAnnotations()));
        }
        return api;
    }
}
