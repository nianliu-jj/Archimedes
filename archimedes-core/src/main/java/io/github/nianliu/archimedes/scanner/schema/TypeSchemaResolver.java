package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.model.FieldInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请求体/响应体类型结构解析器：把 {@link Type} 反射解析为 {@link FieldInfo} 字段树，
 * 供 UI 生成示例 JSON 预填与字段说明表。
 *
 * <p>设计要点：
 * <ul>
 *   <li>解包常见包装类型（ResponseEntity/HttpEntity/Optional/CompletableFuture/Mono 等），
 *       集合/数组/Flux 标记 array 并对元素递归；</li>
 *   <li>字段"说明/必填"来自注解的 <b>FQCN 字符串 + 反射读取</b>（Swagger v3/v2、Jackson、
 *       javax/jakarta validation），core 零编译依赖，宿主未用相应注解库时自然为空；</li>
 *   <li>安全阀：深度上限 {@value #MAX_DEPTH}、同路径类重现判循环、一切异常降级为 null，
 *       绝不影响契约主体输出。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public final class TypeSchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(TypeSchemaResolver.class);

    /** 嵌套解析深度上限（根为 0 层）。 */
    private static final int MAX_DEPTH = 4;

    /** 解包"取第一个泛型实参"的包装类型（按 FQCN 匹配，避免对 reactor 等的编译依赖）。 */
    private static final Set<String> UNWRAP_TYPES = new HashSet<>(Arrays.asList(
            "org.springframework.http.ResponseEntity",
            "org.springframework.http.HttpEntity",
            "java.util.Optional",
            "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage",
            "java.util.concurrent.Callable",
            "reactor.core.publisher.Mono"));

    /** Flux 语义 = 元素流，解包后按集合处理。 */
    private static final String FLUX = "reactor.core.publisher.Flux";

    /** 字段说明注解：按优先级 FQCN → 属性名。 */
    private static final String[][] DESCRIPTION_ANNOTATIONS = {
            {"io.swagger.v3.oas.annotations.media.Schema", "description"},
            {"io.swagger.annotations.ApiModelProperty", "value"},
            {"com.fasterxml.jackson.annotation.JsonPropertyDescription", "value"},
    };

    /** 参数说明注解：按优先级 FQCN → 属性名。 */
    private static final String[][] PARAM_DESCRIPTION_ANNOTATIONS = {
            {"io.swagger.v3.oas.annotations.Parameter", "description"},
            {"io.swagger.annotations.ApiParam", "value"},
    };

    /** 命中即视为必填的 validation 注解（javax/jakarta 双系）。 */
    private static final Set<String> REQUIRED_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "javax.validation.constraints.NotNull",
            "javax.validation.constraints.NotBlank",
            "javax.validation.constraints.NotEmpty",
            "jakarta.validation.constraints.NotNull",
            "jakarta.validation.constraints.NotBlank",
            "jakarta.validation.constraints.NotEmpty"));

    private TypeSchemaResolver() {
    }

    /**
     * 解析入口。返回字段树根节点（name 为空串、type 为解包后类型简名、array 标记集合语义）；
     * void/Void 或解析异常返回 null——调用方直接落到契约字段上即可。
     */
    public static FieldInfo resolve(Type type) {
        if (type == null) {
            return null;
        }
        try {
            return resolveNode("", type, null, 0, new ArrayDeque<Class<?>>());
        } catch (Throwable ex) {
            // 防御式：任何宿主类型的怪异形态都不允许影响契约主体
            log.debug("Archimedes: schema resolve failed for {}, degraded to null", type, ex);
            return null;
        }
    }

    /** 从方法参数注解中提取参数说明（Swagger @Parameter/@ApiParam），无则空串。 */
    public static String paramDescription(Annotation[] annotations) {
        for (String[] spec : PARAM_DESCRIPTION_ANNOTATIONS) {
            String value = annotationString(annotations, spec[0], spec[1]);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /* ---------- 递归解析 ---------- */

    /**
     * 解析单个节点。
     *
     * @param name  字段名（根节点空串）
     * @param type  待解析类型
     * @param field 对应的反射字段（根节点/Map 值等无字段来源时为 null，说明与必填取不到）
     * @param depth 当前深度
     * @param path  当前递归路径上的类栈（循环引用检测）
     */
    private static FieldInfo resolveNode(String name, Type type, Field field, int depth, Deque<Class<?>> path) {
        boolean array = false;
        Type current = type;

        // 循环解包：包装取第一个泛型实参；Flux/集合/数组打 array 标记并降到元素类型
        while (true) {
            Class<?> raw = rawClass(current);
            if (raw != null && UNWRAP_TYPES.contains(raw.getName())) {
                current = typeArg(current, 0);
            } else if (raw != null && FLUX.equals(raw.getName())) {
                array = true;
                current = typeArg(current, 0);
            } else if (raw != null && Collection.class.isAssignableFrom(raw)) {
                array = true;
                current = typeArg(current, 0);
            } else if (raw != null && raw.isArray()) {
                array = true;
                current = raw.getComponentType();
            } else if (current instanceof GenericArrayType) {
                array = true;
                current = ((GenericArrayType) current).getGenericComponentType();
            } else {
                break;
            }
        }

        String description = field != null ? fieldDescription(field) : "";
        boolean required = field != null && fieldRequired(field);
        Class<?> raw = rawClass(current);

        // 未绑定的类型变量（如 Result<T> 的 T）/通配符：按 Object 叶子（设计取舍：不做泛型变量绑定解析）
        if (raw == null) {
            return leaf(name, "Object", required, description, array);
        }
        // void 返回：无响应体结构
        if (raw == void.class || raw == Void.class) {
            return null;
        }
        // 枚举：叶子 + 自动列出可选值（录入时可直接对照）
        if (raw.isEnum()) {
            return leaf(name, raw.getSimpleName(), required,
                    mergeDescription(description, "枚举: " + enumValues(raw)), array);
        }
        // Map：展示键值简名，值为 POJO 时把值的字段作为 children（须在 isLeaf 之前判断——Map 也是 java.*）
        if (Map.class.isAssignableFrom(raw)) {
            String display = "Map<" + simpleName(typeArg(current, 0)) + ", " + simpleName(typeArg(current, 1)) + ">";
            Type valueType = typeArg(current, 1);
            Class<?> valueRaw = rawClass(valueType);
            List<FieldInfo> children = Collections.emptyList();
            if (valueRaw != null && !isLeaf(valueRaw) && !valueRaw.isEnum()
                    && !Map.class.isAssignableFrom(valueRaw)
                    && depth < MAX_DEPTH && !path.contains(valueRaw)) {
                path.push(valueRaw);
                try {
                    children = resolveFields(valueRaw, depth, path);
                } finally {
                    path.pop();
                }
            }
            return new FieldInfo(name, display, required, description, array, children);
        }
        // 平台类型叶子：基本型/包装/字符串/数字/时间日期 + java/javax/jakarta/spring 兜底
        if (isLeaf(raw)) {
            return leaf(name, raw.getSimpleName(), required, description, array);
        }
        // POJO：深度与循环双保护后展开字段
        if (depth >= MAX_DEPTH) {
            return leaf(name, raw.getSimpleName(), required, mergeDescription(description, "(已达深度上限)"), array);
        }
        if (path.contains(raw)) {
            return leaf(name, raw.getSimpleName(), required, mergeDescription(description, "(递归引用)"), array);
        }
        path.push(raw);
        try {
            return new FieldInfo(name, raw.getSimpleName(), required, description, array,
                    resolveFields(raw, depth, path));
        } finally {
            path.pop();
        }
    }

    /** 沿继承链展开 POJO 的可序列化字段（到 java.* 父类为止）。 */
    private static List<FieldInfo> resolveFields(Class<?> raw, int depth, Deque<Class<?>> path) {
        List<FieldInfo> children = new ArrayList<>();
        for (Class<?> c = raw; c != null && !c.getName().startsWith("java."); c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                // 跳过非数据字段与 Jackson 显式忽略的字段
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic()) {
                    continue;
                }
                if (hasAnnotation(f.getAnnotations(), "com.fasterxml.jackson.annotation.JsonIgnore")) {
                    continue;
                }
                // @JsonProperty 覆盖序列化字段名
                String fieldName = annotationString(f.getAnnotations(),
                        "com.fasterxml.jackson.annotation.JsonProperty", "value");
                if (fieldName == null || fieldName.isEmpty()) {
                    fieldName = f.getName();
                }
                FieldInfo child = resolveNode(fieldName, f.getGenericType(), f, depth + 1, path);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    /* ---------- 类型判定与工具 ---------- */

    private static FieldInfo leaf(String name, String type, boolean required, String description, boolean array) {
        return new FieldInfo(name, type, required, description, array, Collections.<FieldInfo>emptyList());
    }

    /** 取 Type 的原始 Class：Class 直接返回，ParameterizedType 取 rawType；类型变量/通配符等返回 null。 */
    private static Class<?> rawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            return raw instanceof Class ? (Class<?>) raw : null;
        }
        return null;
    }

    /** 取第 i 个泛型实参；非参数化类型（原始类型使用）时按 Object 处理。 */
    private static Type typeArg(Type type, int index) {
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (index < args.length) {
                return args[index];
            }
        }
        return Object.class;
    }

    private static String simpleName(Type type) {
        Class<?> raw = rawClass(type);
        return raw != null ? raw.getSimpleName() : "Object";
    }

    /** 平台类型判定：这些类型不再展开字段。 */
    private static boolean isLeaf(Class<?> raw) {
        if (raw.isPrimitive() || raw == Object.class
                || raw == String.class || CharSequence.class.isAssignableFrom(raw)
                || Number.class.isAssignableFrom(raw)
                || raw == Boolean.class || raw == Character.class
                || java.util.Date.class.isAssignableFrom(raw)) {
            return true;
        }
        String name = raw.getName();
        // java.time.* 等时间类型与其余平台/框架类型统一按叶子（Collection/Map 已在前置分支消化）
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("jdk.") || name.startsWith("sun.")
                || name.startsWith("org.springframework.");
    }

    private static String enumValues(Class<?> enumClass) {
        Object[] constants = enumClass.getEnumConstants();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; constants != null && i < constants.length; i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(((Enum<?>) constants[i]).name());
        }
        return sb.toString();
    }

    /** 说明合并：注解说明在前，自动注记（枚举值/递归/深度）在后。 */
    private static String mergeDescription(String base, String note) {
        if (base == null || base.isEmpty()) {
            return note;
        }
        return base + "（" + note + "）";
    }

    /* ---------- 注解 FQCN 反射读取（零编译依赖） ---------- */

    private static String fieldDescription(Field field) {
        for (String[] spec : DESCRIPTION_ANNOTATIONS) {
            String value = annotationString(field.getAnnotations(), spec[0], spec[1]);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static boolean fieldRequired(Field field) {
        Annotation[] annotations = field.getAnnotations();
        for (Annotation annotation : annotations) {
            if (REQUIRED_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        // Swagger v3：requiredMode()==REQUIRED 或（弃用的）required()==true
        String mode = annotationString(annotations, "io.swagger.v3.oas.annotations.media.Schema", "requiredMode");
        if ("REQUIRED".equals(mode)) {
            return true;
        }
        if ("true".equals(annotationString(annotations, "io.swagger.v3.oas.annotations.media.Schema", "required"))) {
            return true;
        }
        // Swagger v2
        return "true".equals(annotationString(annotations, "io.swagger.annotations.ApiModelProperty", "required"));
    }

    private static boolean hasAnnotation(Annotation[] annotations, String fqcn) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().equals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    /** 按 FQCN 定位注解并反射读取属性；注解不存在/属性不存在/读取异常一律返回 null。 */
    private static String annotationString(Annotation[] annotations, String fqcn, String attribute) {
        for (Annotation annotation : annotations) {
            if (!annotation.annotationType().getName().equals(fqcn)) {
                continue;
            }
            try {
                Object value = annotation.annotationType().getMethod(attribute).invoke(annotation);
                return value == null ? null : String.valueOf(value);
            } catch (Exception ex) {
                // 属性缺失（注解版本差异）视为未提供
                return null;
            }
        }
        return null;
    }
}
