package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
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
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public class StompMappingScanner implements WebSocketApiContributor {

    private final List<SimpAnnotationMethodMessageHandler> messageHandlers;

    public StompMappingScanner(List<SimpAnnotationMethodMessageHandler> messageHandlers) {
        this.messageHandlers = messageHandlers;
    }

    /**
     * 遍历消息处理器的映射表：按方法上是否有 @SubscribeMapping 区分订阅/普通消息，
     * 一个目的地条件可含多个 pattern，逐一展开为独立契约，最终按路径排序。
     */
    @Override
    public List<WsApiInfo> contribute() {
        List<WsApiInfo> result = new ArrayList<>();
        for (SimpAnnotationMethodMessageHandler handler : messageHandlers) {
            for (Map.Entry<SimpMessageMappingInfo, HandlerMethod> entry : handler.getHandlerMethods().entrySet()) {
                HandlerMethod method = entry.getValue();
                // @SubscribeMapping 表示订阅端点，其余（@MessageMapping）为普通消息处理
                String kind = method.hasMethodAnnotation(SubscribeMapping.class)
                        ? WsApiInfo.KIND_STOMP_SUBSCRIBE
                        : WsApiInfo.KIND_STOMP_MESSAGE;
                // 单个映射可声明多个目的地 pattern，逐一展开为独立契约条目
                for (String pattern : entry.getKey().getDestinationConditions().getPatterns()) {
                    WsApiInfo info = new WsApiInfo(kind, pattern,
                            method.getBeanType().getName(), method.getMethod().getName(), false);
                    // STOMP 方法级 description 来自方法上的 @ApiDoc（description→summary→value）
                    info.setDescription(TypeSchemaResolver.docText(method.getMethod().getAnnotations()));
                    result.add(info);
                }
            }
        }
        result.sort(Comparator.comparing(WsApiInfo::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
