package io.github.nianliu.archimedes.exampleall.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 装配：一个配置类同时开启三种形态——
 * ① @ServerEndpoint（需 ServerEndpointExporter 导出 Bean 形态的注解端点）；
 * ② WebSocketConfigurer 注册的 handler（普通 + SockJS 两个路径）；
 * ③ STOMP 消息代理（握手端点 /ws/stomp，应用前缀 /app，广播前缀 /topic）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketAllConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    /** 导出容器内 @ServerEndpoint Bean（内嵌容器场景必须显式注册） */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /** 形态②：注册 handler 端点——普通路径 + SockJS 路径（契约分别为 sockJs=false/true） */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EchoWebSocketHandler(), "/ws/echo");
        registry.addHandler(new EchoWebSocketHandler(), "/ws/echo-sockjs").withSockJS();
    }

    /** 形态③：STOMP 握手端点（契约 kind = STOMP_ENDPOINT） */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stomp");
    }

    /** STOMP 路由前缀：/app/** 进 @MessageMapping，/topic/** 走内置简单代理广播 */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
