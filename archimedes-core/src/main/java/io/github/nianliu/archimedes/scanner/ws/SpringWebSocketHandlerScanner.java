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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class SpringWebSocketHandlerScanner implements WebSocketApiContributor {

    private final List<SimpleUrlHandlerMapping> handlerMappings;

    public SpringWebSocketHandlerScanner(List<SimpleUrlHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    /** 遍历所有 URL 映射的 path→handler 条目逐个描述，最终按路径排序输出。 */
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

    /**
     * 描述单个端点：从普通/SockJS 请求处理器取出底层 WebSocketHandler，
     * 若为 SubProtocolWebSocketHandler 则判为 STOMP 握手端点，否则为普通 handler 端点；非 WS 处理器跳过。
     */
    private void describe(String path, Object urlHandler, List<WsApiInfo> result) {
        WebSocketHandler wsHandler = null;
        boolean sockJs = false;
        if (urlHandler instanceof WebSocketHttpRequestHandler) {
            wsHandler = ((WebSocketHttpRequestHandler) urlHandler).getWebSocketHandler();
        } else if (urlHandler instanceof SockJsHttpRequestHandler) {
            wsHandler = ((SockJsHttpRequestHandler) urlHandler).getWebSocketHandler();
            sockJs = true;
        }
        // 映射中可能混有非 WebSocket 处理器（如静态资源），非 WS 端点直接跳过
        if (wsHandler == null) {
            return;
        }
        WebSocketHandler unwrapped = unwrap(wsHandler);
        // 底层 handler 是 SubProtocolWebSocketHandler 即代表 STOMP 子协议握手入口
        String kind = unwrapped instanceof SubProtocolWebSocketHandler
                ? WsApiInfo.KIND_STOMP_ENDPOINT
                : WsApiInfo.KIND_HANDLER;
        // SockJS 注册路径形如 /ws/chat/**，去掉通配后展示注册前缀
        String displayPath = sockJs && path.endsWith("/**")
                ? path.substring(0, path.length() - 3)
                : path;
        result.add(new WsApiInfo(kind, displayPath, unwrapped.getClass().getName(), null, sockJs));
    }

    /** 剥除 WebSocketHandlerDecorator 装饰层（如 ExceptionWebSocketHandlerDecorator），取真实业务 handler。 */
    private WebSocketHandler unwrap(WebSocketHandler handler) {
        WebSocketHandler current = handler;
        while (current instanceof WebSocketHandlerDecorator) {
            current = ((WebSocketHandlerDecorator) current).getDelegate();
        }
        return current;
    }
}
