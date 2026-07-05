package io.github.nianliu.archimedes.example.ws;

import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/native")
public class NativeEchoEndpoint {

    @OnMessage
    public String onMessage(String message) {
        return "native-echo: " + message;
    }
}
