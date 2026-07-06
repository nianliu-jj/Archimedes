package io.github.nianliu.archimedes.exampleall.ws;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 形态三：STOMP 消息控制器。
 * 契约中 @MessageMapping → kind = STOMP_MESSAGE（目的地 /app/chat.send），
 * @SubscribeMapping → kind = STOMP_SUBSCRIBE。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Controller
public class StompChatController {

    /** 客户端 SEND /app/chat.send 时触发 */
    @MessageMapping("/chat.send")
    public String send(String message) {
        return "chat: " + message;
    }

    /** 客户端 SUBSCRIBE /app/chat.history 时的一次性应答 */
    @SubscribeMapping("/chat.history")
    public String history() {
        return "(empty history)";
    }
}
