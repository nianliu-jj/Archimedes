package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.model.WsApiInfo;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描 Spring WebSocket 基础设施注册在 SimpleUrlHandlerMapping 中的端点：
 * WebSocketHttpRequestHandler / SockJsHttpRequestHandler 记为 HANDLER（SockJS 打标），
 * 其底层 handler 为 SubProtocolWebSocketHandler 的是 STOMP 握手端点，记为 STOMP_ENDPOINT。
 */
public class SpringWebSocketHandlerScanner implements WebSocketApiContributor {

    private final List<SimpleUrlHandlerMapping> handlerMappings;

    public SpringWebSocketHandlerScanner(List<SimpleUrlHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public List<WsApiInfo> contribute() {
        List<WsApiInfo> result = new ArrayList<>();
        for (SimpleUrlHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<String, Object> entry : mapping.getHandlerMap().entrySet()) {
                describe(entry.getKey(), entry.getValue(), result);
            }
        }
        result.sort(Comparator.comparing(WsApiInfo::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private void describe(String path, Object urlHandler, List<WsApiInfo> result) {
        WebSocketHandler wsHandler = null;
        boolean sockJs = false;
        if (urlHandler instanceof WebSocketHttpRequestHandler) {
            wsHandler = ((WebSocketHttpRequestHandler) urlHandler).getWebSocketHandler();
        } else if (urlHandler instanceof SockJsHttpRequestHandler) {
            wsHandler = ((SockJsHttpRequestHandler) urlHandler).getWebSocketHandler();
            sockJs = true;
        }
        if (wsHandler == null) {
            return;
        }
        WebSocketHandler unwrapped = unwrap(wsHandler);
        String kind = unwrapped instanceof SubProtocolWebSocketHandler
                ? WsApiInfo.KIND_STOMP_ENDPOINT
                : WsApiInfo.KIND_HANDLER;
        // SockJS 注册路径形如 /ws/chat/**，去掉通配后展示注册前缀
        String displayPath = sockJs && path.endsWith("/**")
                ? path.substring(0, path.length() - 3)
                : path;
        result.add(new WsApiInfo(kind, displayPath, unwrapped.getClass().getName(), null, sockJs));
    }

    private WebSocketHandler unwrap(WebSocketHandler handler) {
        WebSocketHandler current = handler;
        while (current instanceof WebSocketHandlerDecorator) {
            current = ((WebSocketHandlerDecorator) current).getDelegate();
        }
        return current;
    }
}
