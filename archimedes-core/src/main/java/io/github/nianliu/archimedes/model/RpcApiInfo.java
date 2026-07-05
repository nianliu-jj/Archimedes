package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;

/**
 * RPC 类协议的统一契约模型：四类协议（DUBBO/GRPC/SOFA_TR/TRPC）以 protocol 字段区分，
 * 汇入 /apis 的同一 rpcApis 分组。
 */
public class RpcApiInfo {

    public static final String PROTOCOL_DUBBO = "DUBBO";
    public static final String PROTOCOL_GRPC = "GRPC";
    public static final String PROTOCOL_SOFA_TR = "SOFA_TR";
    public static final String PROTOCOL_TRPC = "TRPC";

    private String protocol;

    /** 接口/服务全限定名。 */
    private String serviceName;

    private String version;

    private String group;

    private List<RpcMethodInfo> methods;

    public RpcApiInfo() {
    }

    public RpcApiInfo(String protocol, String serviceName, String version, String group,
                      List<RpcMethodInfo> methods) {
        this.protocol = protocol;
        this.serviceName = serviceName;
        this.version = version;
        this.group = group;
        this.methods = methods == null ? Collections.<RpcMethodInfo>emptyList() : methods;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public List<RpcMethodInfo> getMethods() {
        return methods;
    }

    public void setMethods(List<RpcMethodInfo> methods) {
        this.methods = methods;
    }
}
