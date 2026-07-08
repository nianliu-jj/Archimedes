package io.github.nianliu.archimedes.scanner.ws;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.model.WsApiInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.stereotype.Controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StompMappingScannerTest {

    @Controller
    static class GreetController {

        @MessageMapping("/greet")
        @ApiDoc(summary = "发送消息")
        public String greet(String name) {
            return "hi " + name;
        }

        @SubscribeMapping("/init")
        public String init() {
            return "init";
        }
    }

    @Test
    void scansMessageAndSubscribeMappings() {
        SimpAnnotationMethodMessageHandler handler = new SimpAnnotationMethodMessageHandler(
                mock(SubscribableChannel.class), mock(MessageChannel.class),
                mock(SimpMessageSendingOperations.class));
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("greetController", GreetController.class);
        context.refresh();
        handler.setApplicationContext(context);
        handler.afterPropertiesSet();

        List<WsApiInfo> result = new StompMappingScanner(List.of(handler)).contribute();

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(w -> {
            assertThat(w.getKind()).isEqualTo(WsApiInfo.KIND_STOMP_MESSAGE);
            assertThat(w.getPath()).isEqualTo("/greet");
            assertThat(w.getHandlerMethod()).isEqualTo("greet");
            assertThat(w.getHandlerClass()).isEqualTo(GreetController.class.getName());
            // 方法级 description 来自方法上的 @ApiDoc(summary)
            assertThat(w.getDescription()).isEqualTo("发送消息");
        });
        assertThat(result).anySatisfy(w -> {
            assertThat(w.getKind()).isEqualTo(WsApiInfo.KIND_STOMP_SUBSCRIBE);
            assertThat(w.getPath()).isEqualTo("/init");
            assertThat(w.getHandlerMethod()).isEqualTo("init");
        });
    }
}
