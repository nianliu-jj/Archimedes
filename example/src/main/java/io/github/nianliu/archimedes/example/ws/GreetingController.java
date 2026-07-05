package io.github.nianliu.archimedes.example.ws;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GreetingController {

    @MessageMapping("/greet")
    @SendTo("/topic/greetings")
    public String greet(String name) {
        return "hello, " + name;
    }

    @SubscribeMapping("/init")
    public String init() {
        return "welcome";
    }
}
