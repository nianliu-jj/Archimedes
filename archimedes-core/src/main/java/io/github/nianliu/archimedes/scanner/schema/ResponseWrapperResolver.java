package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 统一响应包装体解析器：当配置了 {@code archimedes.api.response-wrapper.wrapper-class} 时，
 * 把方法真实返回类型的字段树（innerSchema）嵌入包装类的 data 字段位置，
 * 返回完整包装体 FieldInfo；未启用/豁免/加载失败/无 data 字段时原样返回 innerSchema。
 *
 * <p>豁免（不套壳）三种：方法或 Controller 类标 {@code @NoApiWrapper}、
 * 返回类型即包装类或其子类、返回类型为 {@code ResponseEntity}。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
public class ResponseWrapperResolver {

    private static final Logger log = LoggerFactory.getLogger(ResponseWrapperResolver.class);

    /** ResponseEntity 的 FQCN（按字符串判断，避免对具体类型的强绑定）。 */
    private static final String RESPONSE_ENTITY = "org.springframework.http.ResponseEntity";

    private final ArchimedesApiProperties properties;

    public ResponseWrapperResolver(ArchimedesApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 把 innerSchema 包进配置的包装体；未启用/豁免/降级时原样返回 innerSchema。
     *
     * @param innerSchema    方法真实返回类型的字段树（可能为 null，表示 void 无响应体）
     * @param method         处理方法
     * @param controllerType 所属 Controller 类（用于类级 @NoApiWrapper 判定）
     * @return 完整包装体 FieldInfo，或原样 innerSchema
     */
    public FieldInfo wrap(FieldInfo innerSchema, Method method, Class<?> controllerType) {
        ArchimedesApiProperties.ResponseWrapper cfg = properties.getResponseWrapper();
        // 1) 未启用 / 未配置包装类 → 原样返回
        if (cfg == null || !cfg.isEnabled() || cfg.getWrapperClass() == null || cfg.getWrapperClass().isEmpty()) {
            return innerSchema;
        }
        // 2) 豁免判定
        if (isExempt(method, controllerType, cfg.getWrapperClass())) {
            return innerSchema;
        }
        // 3) 加载包装类；失败视为未启用（不硬失败）
        Class<?> wrapperClass = loadClass(cfg.getWrapperClass());
        if (wrapperClass == null) {
            log.debug("Archimedes: 响应包装类 {} 加载失败，responseSchema 保持内层结构", cfg.getWrapperClass());
            return innerSchema;
        }
        // 4) 解析包装类字段树，把 data 字段的结构替换为 innerSchema
        FieldInfo wrapperTree = TypeSchemaResolver.resolve(wrapperClass);
        if (wrapperTree == null || wrapperTree.getChildren() == null) {
            return innerSchema;
        }
        FieldInfo dataNode = findChild(wrapperTree, cfg.getDataField());
        if (dataNode == null) {
            log.warn("Archimedes: 响应包装类 {} 中不存在 data 字段 '{}'，responseSchema 保持内层结构",
                    cfg.getWrapperClass(), cfg.getDataField());
            return innerSchema;
        }
        // innerSchema 为 null（void 内层）时不替换，data 节点保持包装类原样
        if (innerSchema != null) {
            dataNode.setType(innerSchema.getType());
            dataNode.setArray(innerSchema.isArray());
            dataNode.setChildren(innerSchema.getChildren());
            dataNode.setEnumValues(innerSchema.getEnumValues());
        }
        return wrapperTree;
    }

    /** 三类豁免判定。 */
    private boolean isExempt(Method method, Class<?> controllerType, String wrapperClassName) {
        // (a) 方法或类标注 @NoApiWrapper
        if (method.isAnnotationPresent(NoApiWrapper.class)
                || (controllerType != null && controllerType.isAnnotationPresent(NoApiWrapper.class))) {
            return true;
        }
        Class<?> returnType = method.getReturnType();
        // (b) 返回类型是 ResponseEntity（自定义状态码，绕过 advice）
        if (RESPONSE_ENTITY.equals(returnType.getName())) {
            return true;
        }
        // (c) 返回类型本身就是包装类或其子类（本就直接返回壳，不二次包装）
        Class<?> wrapperClass = loadClass(wrapperClassName);
        return wrapperClass != null && wrapperClass.isAssignableFrom(returnType);
    }

    /** 在字段树的直接子节点中按名查找；无则 null。 */
    private FieldInfo findChild(FieldInfo tree, String name) {
        if (tree.getChildren() == null || name == null) {
            return null;
        }
        for (FieldInfo c : tree.getChildren()) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    /** 按 FQCN 加载类；失败返回 null（不抛）。 */
    private Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn, false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }
}
