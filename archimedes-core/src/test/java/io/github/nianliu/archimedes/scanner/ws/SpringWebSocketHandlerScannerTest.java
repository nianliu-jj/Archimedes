package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.model.WsApiInfo;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.ExceptionWebSocketHandlerDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.sockjs.SockJsService;
import org.springframework.web.socket.sockjs.support.SockJsHttpRequestHandler;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpringWebSocketHandlerScannerTest {

    @ApiModule(description = "回声端点")
    static class ChatHandler extends TextWebSocketHandler {
    }

    private SimpleUrlHandlerMapping mapping(Map<String, Object> urlMap) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setApplicationContext(new StaticWebApplicationContext());
        return mapping;
    }

    @Test
    void scansPlainHandlerAndUnwrapsDecorators() {
        WebSocketHandler decorated = new ExceptionWebSocketHandlerDecorator(new ChatHandler());
        SimpleUrlHandlerMapping m = mapping(Map.of(
                "/ws/chat", new WebSocketHttpRequestHandler(decorated),
                "/other", new Object()));

        List<WsApiInfo> result = new SpringWebSocketHandlerScanner(List.of(m)).contribute();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKind()).isEqualTo(WsApiInfo.KIND_HANDLER);
        assertThat(result.get(0).getPath()).isEqualTo("/ws/chat");
        assertThat(result.get(0).getHandlerClass()).isEqualTo(ChatHandler.class.getName());
        assertThat(result.get(0).isSockJs()).isFalse();
        // handler 类级 description 来自类上的 @ApiModule(description)
        assertThat(result.get(0).getDescription()).isEqualTo("回声端点");
    }

    @Test
    void marksSockJsAndTrimsWildcard() {
        SockJsHttpRequestHandler sockJs =
                new SockJsHttpRequestHandler(mock(SockJsService.class), new ChatHandler());
        SimpleUrlHandlerMapping m = mapping(Map.of("/ws/sock/**", sockJs));

        List<WsApiInfo> result = new SpringWebSocketHandlerScanner(List.of(m)).contribute();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPath()).isEqualTo("/ws/sock");
        assertThat(result.get(0).isSockJs()).isTrue();
    }

    @Test
    void classifiesStompHandshakeEndpoint() {
        SubProtocolWebSocketHandler stompHandler =
                new SubProtocolWebSocketHandler(mock(MessageChannel.class), mock(SubscribableChannel.class));
        SimpleUrlHandlerMapping m = mapping(Map.of("/stomp", new WebSocketHttpRequestHandler(stompHandler)));

        List<WsApiInfo> result = new SpringWebSocketHandlerScanner(List.of(m)).contribute();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKind()).isEqualTo(WsApiInfo.KIND_STOMP_ENDPOINT);
        assertThat(result.get(0).getPath()).isEqualTo("/stomp");
    }
}
