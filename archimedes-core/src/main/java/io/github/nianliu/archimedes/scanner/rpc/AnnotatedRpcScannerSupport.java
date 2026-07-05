package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 反射式注解 RPC 扫描共用骨架：不引入目标框架编译依赖，按注解 FQCN 收集服务 Bean，
 * 解析服务接口（注解接口属性 → 唯一实现接口 → 实现类兜底）、反射方法签名；
 * 单个 Bean 解析失败仅跳过该 Bean。子类只负责把注解属性映射为 RpcApiInfo。
 */
public abstract class AnnotatedRpcScannerSupport implements RpcApiContributor {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedRpcScannerSupport.class);

    private final ApplicationContext applicationContext;
    private final String annotationClassName;
    private final String protocol;

    protected AnnotatedRpcScannerSupport(ApplicationContext applicationContext,
                                         String annotationClassName, String protocol) {
        this.applicationContext = applicationContext;
        this.annotationClassName = annotationClassName;
        this.protocol = protocol;
    }

    /** 注解中声明服务接口的属性名；无此属性的协议返回 null。 */
    protected String interfaceAttributeName() {
        return null;
    }

    protected abstract RpcApiInfo build(AnnotationAttributes attributes, ServiceTarget target,
                                        List<RpcMethodInfo> methods);

    @Override
    public List<RpcApiInfo> contribute() {
        Class<? extends Annotation> annotationType = resolveAnnotationType();
        if (annotationType == null) {
            return new ArrayList<>();
        }
        List<RpcApiInfo> result = new ArrayList<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(annotationType);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            try {
                RpcApiInfo api = describe(entry.getValue());
                if (api != null) {
                    result.add(api);
                }
            } catch (Exception ex) {
                log.debug("Archimedes: 解析 {} 服务 Bean '{}' 失败，已跳过", protocol, entry.getKey(), ex);
            }
        }
        result.sort(Comparator.comparing(RpcApiInfo::getServiceName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private RpcApiInfo describe(Object bean) {
        Class<?> userClass = ClassUtils.getUserClass(bean);
        // nestedAnnotationsAsMap=true：嵌套注解（如 @SofaServiceBinding）以 AnnotationAttributes 呈现
        AnnotationAttributes attributes = AnnotatedElementUtils.getMergedAnnotationAttributes(
                userClass, annotationClassName, false, true);
        if (attributes == null) {
            return null;
        }
        ServiceTarget target = resolveServiceTarget(userClass, attributes);
        return build(attributes, target, reflectMethods(target.type));
    }

    private ServiceTarget resolveServiceTarget(Class<?> userClass, AnnotationAttributes attributes) {
        String attributeName = interfaceAttributeName();
        if (attributeName != null && attributes.containsKey(attributeName)) {
            Object declared = attributes.get(attributeName);
            if (declared instanceof Class && declared != void.class && declared != Object.class) {
                return new ServiceTarget((Class<?>) declared, false);
            }
        }
        Class<?>[] interfaces = userClass.getInterfaces();
        if (interfaces.length == 1) {
            return new ServiceTarget(interfaces[0], false);
        }
        return new ServiceTarget(userClass, true);
    }

    private List<RpcMethodInfo> reflectMethods(Class<?> type) {
        List<RpcMethodInfo> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            List<String> parameterTypes = new ArrayList<>();
            for (Class<?> parameterType : method.getParameterTypes()) {
                parameterTypes.add(parameterType.getName());
            }
            methods.add(new RpcMethodInfo(method.getName(), parameterTypes,
                    method.getReturnType().getName()));
        }
        methods.sort(Comparator.comparing(RpcMethodInfo::getMethodName));
        return methods;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> resolveAnnotationType() {
        try {
            Class<?> type = ClassUtils.forName(annotationClassName, getClass().getClassLoader());
            return type.isAnnotation() ? (Class<? extends Annotation>) type : null;
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    protected static final class ServiceTarget {

        final Class<?> type;

        /** true = 未能解析出接口，serviceName 以实现类兜底。 */
        final boolean fromImplementation;

        ServiceTarget(Class<?> type, boolean fromImplementation) {
            this.type = type;
            this.fromImplementation = fromImplementation;
        }

        public Class<?> getType() {
            return type;
        }

        public boolean isFromImplementation() {
            return fromImplementation;
        }
    }
}
