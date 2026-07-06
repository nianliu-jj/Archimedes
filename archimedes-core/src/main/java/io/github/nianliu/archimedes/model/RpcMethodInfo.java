package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单个 RPC 方法签名契约，作为 {@link RpcApiInfo#getMethods()} 的元素。
 * <p>设计要点：仅承载「方法名 + 参数类型列表 + 返回类型」这一跨协议通用的最小签名信息，
 * 协议特有的差异（如 gRPC 的 streaming 形态）统一下沉到 metadata 扩展位，避免为每种协议新增字段。
 * metadata 为协议特有扩展位，可为 null。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class RpcMethodInfo {

    /** 方法名。 */
    private String methodName;
    /** 参数类型全限定名列表（按声明顺序）；无参方法为空列表。 */
    private List<String> parameterTypes;
    /** 返回类型全限定名。 */
    private String returnType;
    /** 协议特有的方法级扩展元数据（如 gRPC streaming 形态），可为 null。 */
    private Map<String, String> metadata;

    public RpcMethodInfo() {
    }

    /** 便捷构造：不含扩展元数据。 */
    public RpcMethodInfo(String methodName, List<String> parameterTypes, String returnType) {
        this(methodName, parameterTypes, returnType, null);
    }

    /** 全量构造：parameterTypes 为 null 时兜底空列表，保证序列化恒为数组。 */
    public RpcMethodInfo(String methodName, List<String> parameterTypes, String returnType,
                         Map<String, String> metadata) {
        this.methodName = methodName;
        // 空参数列表兜底，避免前端对 null 与 [] 做两套判断
        this.parameterTypes = parameterTypes == null ? Collections.<String>emptyList() : parameterTypes;
        this.returnType = returnType;
        this.metadata = metadata;
    }

    public String getMethodName() {
        return methodName;
    }

    /** 设置方法名。 */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    /** 设置参数类型列表。 */
    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public String getReturnType() {
        return returnType;
    }

    /** 设置返回类型。 */
    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** 设置协议特有扩展元数据。 */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
