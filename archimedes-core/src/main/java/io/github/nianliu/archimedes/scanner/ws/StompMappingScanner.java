package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.model.WsApiInfo;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.simp.SimpMessageMappingInfo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描 STOMP 注解消息映射：@MessageMapping → STOMP_MESSAGE，@SubscribeMapping → STOMP_SUBSCRIBE。
 * 仅依赖 spring-messaging；STOMP 握手端点由 SpringWebSocketHandlerScanner 负责。
 */
public class StompMappingScanner implements WebSocketApiContributor {

    private final List<SimpAnnotationMethodMessageHandler> messageHandlers;

    public StompMappingScanner(List<SimpAnnotationMethodMessageHandler> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    @Override
    public List<WsApiInfo> contribute() {
        List<WsApiInfo> result = new ArrayList<>();
        for (SimpAnnotationMethodMessageHandler handler : messageHandlers) {
            for (Map.Entry<SimpMessageMappingInfo, HandlerMethod> entry : handler.getHandlerMethods().entrySet()) {
                HandlerMethod method = entry.getValue();
                String kind = method.hasMethodAnnotation(SubscribeMapping.class)
                        ? WsApiInfo.KIND_STOMP_SUBSCRIBE
                        : WsApiInfo.KIND_STOMP_MESSAGE;
                for (String pattern : entry.getKey().getDestinationConditions().getPatterns()) {
                    result.add(new WsApiInfo(kind, pattern,
                            method.getBeanType().getName(), method.getMethod().getName(), false));
                }
            }
        }
        result.sort(Comparator.comparing(WsApiInfo::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
