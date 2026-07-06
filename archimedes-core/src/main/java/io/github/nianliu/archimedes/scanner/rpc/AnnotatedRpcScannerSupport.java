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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public abstract class AnnotatedRpcScannerSupport implements RpcApiContributor {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedRpcScannerSupport.class);

    private final ApplicationContext applicationContext;

    /** 目标服务注解的全限定名（如 @SofaService/@TRpcService），按 FQCN 反射解析以规避编译依赖。 */
    private final String annotationClassName;

    /** 协议标识，用于 RpcApiInfo 归类与日志。 */
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

    /** 由子类将协议特有的注解属性（uniqueId/version/group/bindings 等）映射为 RpcApiInfo。 */
    protected abstract RpcApiInfo build(AnnotationAttributes attributes, ServiceTarget target,
                                        List<RpcMethodInfo> methods);

    /**
     * 扫描入口：注解类不在 classpath（协议未引入）时返回空；否则收集所有带该注解的 Bean，
     * 逐个 describe，单 Bean 失败仅 debug 记录并跳过，最终按服务名排序。
     */
    @Override
    public List<RpcApiInfo> contribute() {
        Class<? extends Annotation> annotationType = resolveAnnotationType();
        // 注解类加载失败即代表宿主未引入该 RPC 框架，直接返回空契约
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
                // 单个 Bean 注解形态异常不应影响其余服务，降级为 debug
                log.debug("Archimedes: 解析 {} 服务 Bean '{}' 失败，已跳过", protocol, entry.getKey(), ex);
            }
        }
        result.sort(Comparator.comparing(RpcApiInfo::getServiceName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /** 解析单个服务 Bean：合并读取注解属性 → 确定服务接口目标 → 反射方法列表 → 交子类组装。 */
    private RpcApiInfo describe(Object bean) {
        // 取用户类：Bean 可能是 CGLIB 代理，须还原真实类才能读到原始注解
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

    /**
     * 确定服务接口目标，三级回退：
     * 1) 注解显式声明的接口属性（排除 void/Object 占位默认值）；
     * 2) 实现类恰好实现单一接口时取该接口；
     * 3) 否则（无接口或多接口无法判定）以实现类兜底，并标记 fromImplementation。
     */
    private ServiceTarget resolveServiceTarget(Class<?> userClass, AnnotationAttributes attributes) {
        String attributeName = interfaceAttributeName();
        if (attributeName != null && attributes.containsKey(attributeName)) {
            Object declared = attributes.get(attributeName);
            // void.class/Object.class 是注解未显式指定接口时的默认占位，须排除
            if (declared instanceof Class && declared != void.class && declared != Object.class) {
                return new ServiceTarget((Class<?>) declared, false);
            }
        }
        Class<?>[] interfaces = userClass.getInterfaces();
        // 单接口实现：该接口即服务契约，最常见形态
        if (interfaces.length == 1) {
            return new ServiceTarget(interfaces[0], false);
        }
        // 无接口或多接口无从判定，以实现类兜底
        return new ServiceTarget(userClass, true);
    }

    /** 反射目标类型的 public 方法作为 RPC 方法签名，跳过 Object 自带方法，按方法名排序。 */
    private List<RpcMethodInfo> reflectMethods(Class<?> type) {
        List<RpcMethodInfo> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            // Object 的 equals/hashCode/toString 等非业务方法排除
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

    /** 按 FQCN 加载注解类型；类不存在（框架未引入）返回 null，也是本扫描器"静默不装配"的开关。 */
    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> resolveAnnotationType() {
        try {
            Class<?> type = ClassUtils.forName(annotationClassName, getClass().getClassLoader());
            return type.isAnnotation() ? (Class<? extends Annotation>) type : null;
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /** 服务接口解析结果：承载最终用于反射方法与命名的类型，及其是否来自实现类兜底。 */
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
