package io.github.nianliu.archimedes.example.boot2.ws;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/native")
public class NativeEchoEndpoint {

    @OnMessage
    public String onMessage(String message) {
        return "native-echo: " + message;
    }
}
