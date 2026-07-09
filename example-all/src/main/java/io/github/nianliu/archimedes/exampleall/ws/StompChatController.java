package io.github.nianliu.archimedes.exampleall.ws;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
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
@ApiModule(name = "STOMP 聊天演示", description = "STOMP 消息控制器：@MessageMapping 发送目的地与 @SubscribeMapping 订阅目的地")
public class StompChatController {

    /** 客户端 SEND /app/chat.send 时触发 */
    @ApiDoc(summary = "发送消息", description = "客户端 SEND /app/chat.send 时触发，回显聊天内容")
    @MessageMapping("/chat.send")
    public String send(String message) {
        return "chat: " + message;
    }

    /** 客户端 SUBSCRIBE /app/chat.history 时的一次性应答 */
    @ApiDoc(summary = "订阅历史", description = "客户端 SUBSCRIBE /app/chat.history 时的一次性应答")
    @SubscribeMapping("/chat.history")
    public String history() {
        return "(empty history)";
    }
}
