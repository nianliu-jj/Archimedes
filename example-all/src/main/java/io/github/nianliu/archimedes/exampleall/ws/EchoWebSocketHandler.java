package io.github.nianliu.archimedes.exampleall.ws;

import io.github.nianliu.archimedes.annotation.ApiModule;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 形态二的处理器：Spring WebSocketHandler（经 WebSocketConfigurer 注册）。
 * 契约中 kind = HANDLER，SockJS 注册路径额外带 sockJs=true 标记。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@ApiModule(name = "回声 WebSocketHandler", description = "Spring WebSocketHandler 文本回声，经 WebSocketConfigurer 注册（含 SockJS）")
public class EchoWebSocketHandler extends TextWebSocketHandler {

    /** 文本回声 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage("echo: " + message.getPayload()));
    }
}
