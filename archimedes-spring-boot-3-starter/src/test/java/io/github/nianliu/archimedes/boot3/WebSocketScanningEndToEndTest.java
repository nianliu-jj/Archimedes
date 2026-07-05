package io.github.nianliu.archimedes.boot3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三种 WebSocket 形态在真实容器中的端到端扫描验证（jakarta 世界）。
 */
@SpringBootTest(classes = WebSocketScanningEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketScanningEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> webSocketApis() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        Map<String, Object> catalog = mapper.readValue(body, new TypeReference<Map<String, Object>>() {
        });
        return (List<Map<String, Object>>) catalog.get("webSocketApis");
    }

    @Test
    void scansHandlerRegistration() throws Exception {
        assertThat(webSocketApis()).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("HANDLER");
            assertThat(w.get("path")).isEqualTo("/ws/chat");
            assertThat((String) w.get("handlerClass")).endsWith("ChatHandler");
        });
    }

    @Test
    void scansServerEndpoint() throws Exception {
        assertThat(webSocketApis()).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("SERVER_ENDPOINT");
            assertThat(w.get("path")).isEqualTo("/ws/echo");
            assertThat((String) w.get("handlerClass")).endsWith("EchoEndpoint");
        });
    }

    @Test
    void scansStompEndpointAndMappings() throws Exception {
        List<Map<String, Object>> ws = webSocketApis();
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_ENDPOINT");
            assertThat(w.get("path")).isEqualTo("/stomp");
        });
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_MESSAGE");
            assertThat(w.get("path")).isEqualTo("/greet");
            assertThat(w.get("handlerMethod")).isEqualTo("greet");
        });
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_SUBSCRIBE");
            assertThat(w.get("path")).isEqualTo("/init");
        });
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        EchoEndpoint echoEndpoint() {
            return new EchoEndpoint();
        }

        @Bean
        ServerEndpointExporter serverEndpointExporter() {
            return new ServerEndpointExporter();
        }

        @Configuration
        @EnableWebSocket
        static class WsConfig implements WebSocketConfigurer {
            @Override
            public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
                registry.addHandler(new ChatHandler(), "/ws/chat");
            }
        }

        @Configuration
        @EnableWebSocketMessageBroker
        static class StompConfig implements WebSocketMessageBrokerConfigurer {
            @Override
            public void registerStompEndpoints(StompEndpointRegistry registry) {
                registry.addEndpoint("/stomp");
            }

            @Override
            public void configureMessageBroker(MessageBrokerRegistry registry) {
                registry.enableSimpleBroker("/topic");
                registry.setApplicationDestinationPrefixes("/app");
            }
        }

        @Controller
        static class GreetController {
            @MessageMapping("/greet")
            public void greet(String name) {
            }

            @SubscribeMapping("/init")
            public String init() {
                return "init";
            }
        }
    }

    static class ChatHandler extends TextWebSocketHandler {
    }

    @ServerEndpoint("/ws/echo")
    static class EchoEndpoint {
        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }
}
