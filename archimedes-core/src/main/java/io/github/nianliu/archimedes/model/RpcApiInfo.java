package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RPC 类协议的统一契约模型：四类协议（DUBBO/GRPC/SOFA_TR/TRPC）以 protocol 字段区分，
 * 汇入 /apis 的同一 rpcApis 分组。metadata 为服务级协议特有扩展位（可为 null）。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class RpcApiInfo {

    /** 协议常量：Dubbo。 */
    public static final String PROTOCOL_DUBBO = "DUBBO";
    /** 协议常量：gRPC。 */
    public static final String PROTOCOL_GRPC = "GRPC";
    /** 协议常量：SOFARPC TR（bolt/tr）。 */
    public static final String PROTOCOL_SOFA_TR = "SOFA_TR";
    /** 协议常量：tRPC。 */
    public static final String PROTOCOL_TRPC = "TRPC";

    /** 协议标识，取上述 PROTOCOL_* 之一。 */
    private String protocol;

    /** 接口/服务全限定名。 */
    private String serviceName;

    /** 服务版本（Dubbo version 等，可为空）。 */
    private String version;

    /** 服务分组（Dubbo group 等，可为空）。 */
    private String group;

    /** 方法契约列表。 */
    private List<RpcMethodInfo> methods;

    /** 协议特有的服务级扩展元数据，可为 null。 */
    private Map<String, String> metadata;

    public RpcApiInfo() {
    }

    /** 便捷构造：不含扩展元数据。 */
    public RpcApiInfo(String protocol, String serviceName, String version, String group,
                      List<RpcMethodInfo> methods) {
        this(protocol, serviceName, version, group, methods, null);
    }

    /** 全量构造：methods 为 null 时兜底空列表，保证序列化恒为数组。 */
    public RpcApiInfo(String protocol, String serviceName, String version, String group,
                      List<RpcMethodInfo> methods, Map<String, String> metadata) {
        this.protocol = protocol;
        this.serviceName = serviceName;
        this.version = version;
        this.group = group;
        this.methods = methods == null ? Collections.<RpcMethodInfo>emptyList() : methods;
        this.metadata = metadata;
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

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
