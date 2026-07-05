package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;

/**
 * {base-path}/apis 的多协议分组响应。约定：协议在宿主中不存在时对应字段为空数组而非缺失；
 * RPC 类协议（Dubbo/gRPC/SOFARPC-TR/tRPC）统一并入 rpcApis（protocol 字段区分）。
 */
public class ApiCatalog {

    private final List<ApiInfo> restApis;
    private final List<WsApiInfo> webSocketApis;
    private final List<RpcApiInfo> rpcApis;

    public ApiCatalog(List<ApiInfo> restApis, List<WsApiInfo> webSocketApis) {
        this(restApis, webSocketApis, null);
    }

    public ApiCatalog(List<ApiInfo> restApis, List<WsApiInfo> webSocketApis, List<RpcApiInfo> rpcApis) {
        this.restApis = restApis == null ? Collections.<ApiInfo>emptyList() : restApis;
        this.webSocketApis = webSocketApis == null ? Collections.<WsApiInfo>emptyList() : webSocketApis;
        this.rpcApis = rpcApis == null ? Collections.<RpcApiInfo>emptyList() : rpcApis;
    }

    public List<ApiInfo> getRestApis() {
        return restApis;
    }

    public List<WsApiInfo> getWebSocketApis() {
        return webSocketApis;
    }

    public List<RpcApiInfo> getRpcApis() {
        return rpcApis;
    }
}
