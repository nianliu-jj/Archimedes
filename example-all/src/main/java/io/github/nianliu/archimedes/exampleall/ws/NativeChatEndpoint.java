package io.github.nianliu.archimedes.exampleall.ws;

import io.github.nianliu.archimedes.annotation.ApiModule;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

/**
 * WebSocket 形态一：JSR-356 注解端点（jakarta @ServerEndpoint）。
 * 必须注册为 Spring Bean（@Component）才能被 Archimedes 扫描到——
 * 契约中 kind = SERVER_ENDPOINT。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Component
@ServerEndpoint("/ws/native/{room}")
@ApiModule(name = "原生 WebSocket 端点", description = "JSR-356 @ServerEndpoint 房间聊天端点，路径含 {room} 变量")
public class NativeChatEndpoint {

    /** 简单回声：演示端点可用即可，扫描只关心 @ServerEndpoint 元数据 */
    @OnMessage
    public String onMessage(@PathParam("room") String room, String message) {
        return "[" + room + "] " + message;
    }
}
