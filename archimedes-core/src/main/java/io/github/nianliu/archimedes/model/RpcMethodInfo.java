package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;

/** 单个 RPC 方法签名。 */
public class RpcMethodInfo {

    private String methodName;
    private List<String> parameterTypes;
    private String returnType;

    public RpcMethodInfo() {
    }

    public RpcMethodInfo(String methodName, List<String> parameterTypes, String returnType) {
        this.methodName = methodName;
        this.parameterTypes = parameterTypes == null ? Collections.<String>emptyList() : parameterTypes;
        this.returnType = returnType;
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
}
