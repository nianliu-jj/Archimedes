package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.model.WsApiInfo;

import java.util.List;

/**
 * WebSocket 契约贡献者 SPI。每种端点形态（@ServerEndpoint / Spring handler / STOMP）各一个实现，
 * 由 starter 按 classpath 条件装配；控制器聚合所有在场贡献者的结果。
 * 这一"模型 + 贡献者 + 条件装配"模式也是后续 RPC/TR 协议扫描器的模板。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface WebSocketApiContributor {

    /** 贡献当前端点形态下宿主暴露的全部 WebSocket 契约。 */
    List<WsApiInfo> contribute();
}
