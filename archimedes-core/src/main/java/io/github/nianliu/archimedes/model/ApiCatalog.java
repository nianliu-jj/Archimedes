package io.github.nianliu.archimedes.model;

import java.util.Collections;
import java.util.List;

/**
 * {base-path}/apis 的多协议分组响应。约定：协议在宿主中不存在时对应字段为空数组而非缺失，
 * 后续新协议（RPC/TR 等）以新增字段的方式扩展。
 */
public class ApiCatalog {

    private final List<ApiInfo> restApis;
    private final List<WsApiInfo> webSocketApis;

    public ApiCatalog(List<ApiInfo> restApis, List<WsApiInfo> webSocketApis) {
        this.restApis = restApis == null ? Collections.<ApiInfo>emptyList() : restApis;
        this.webSocketApis = webSocketApis == null ? Collections.<WsApiInfo>emptyList() : webSocketApis;
    }

    public List<ApiInfo> getRestApis() {
        return restApis;
    }

    public List<WsApiInfo> getWebSocketApis() {
        return webSocketApis;
    }
}
