package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;

/**
 * {base-path}/apis 的多协议分组响应。约定：协议在宿主中不存在时对应字段为空数组而非缺失；
 * RPC 类协议（Dubbo/gRPC/SOFARPC-TR/tRPC）统一并入 rpcApis（protocol 字段区分）。
 * <p>本类为不可变对象：构造后集合引用不再变化，且构造时对 null 入参做空列表兜底，
 * 保证 JSON 序列化时字段恒为数组，前端无需判空。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class ApiCatalog {

    /** REST 契约列表。 */
    private final List<ApiInfo> restApis;
    /** WebSocket 契约列表。 */
    private final List<WsApiInfo> webSocketApis;
    /** RPC 类协议契约列表（四类协议汇总）。 */
    private final List<RpcApiInfo> rpcApis;

    /** 兼容旧版：仅含 REST 与 WebSocket，RPC 分组置空。 */
    public ApiCatalog(List<ApiInfo> restApis, List<WsApiInfo> webSocketApis) {
        this(restApis, webSocketApis, null);
    }

    /** 全量构造：任一入参为 null 时兜底为空列表，确保序列化输出恒为数组。 */
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
