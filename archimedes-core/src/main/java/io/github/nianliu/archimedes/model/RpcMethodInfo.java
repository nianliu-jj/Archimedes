package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 单个 RPC 方法签名。metadata 为协议特有扩展位（如 gRPC 的 streaming 形态），可为 null。 */
public class RpcMethodInfo {

    private String methodName;
    private List<String> parameterTypes;
    private String returnType;
    private Map<String, String> metadata;

    public RpcMethodInfo() {
    }

    public RpcMethodInfo(String methodName, List<String> parameterTypes, String returnType) {
        this(methodName, parameterTypes, returnType, null);
    }

    public RpcMethodInfo(String methodName, List<String> parameterTypes, String returnType,
                         Map<String, String> metadata) {
        this.methodName = methodName;
        this.parameterTypes = parameterTypes == null ? Collections.<String>emptyList() : parameterTypes;
        this.returnType = returnType;
        this.metadata = metadata;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
