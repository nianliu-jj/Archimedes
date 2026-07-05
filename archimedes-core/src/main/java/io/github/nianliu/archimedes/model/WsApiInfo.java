package io.github.nianliu.archimedes.model;

/**
 * 单个 WebSocket 端点/目的地的契约信息。kind 取值：
 * SERVER_ENDPOINT（@ServerEndpoint 注解端点）、HANDLER（WebSocketConfigurer 注册的 handler）、
 * STOMP_ENDPOINT（STOMP 握手端点）、STOMP_MESSAGE（@MessageMapping）、STOMP_SUBSCRIBE（@SubscribeMapping）。
 */
public class WsApiInfo {

    public static final String KIND_SERVER_ENDPOINT = "SERVER_ENDPOINT";
    public static final String KIND_HANDLER = "HANDLER";
    public static final String KIND_STOMP_ENDPOINT = "STOMP_ENDPOINT";
    public static final String KIND_STOMP_MESSAGE = "STOMP_MESSAGE";
    public static final String KIND_STOMP_SUBSCRIBE = "STOMP_SUBSCRIBE";

    private String kind;

    /** 端点路径或 STOMP 目的地 pattern。 */
    private String path;

    private String handlerClass;

    /** 方法级形态（STOMP_MESSAGE/STOMP_SUBSCRIBE）才有；类级形态为 null。 */
    private String handlerMethod;

    private boolean sockJs;

    public WsApiInfo() {
    }

    public WsApiInfo(String kind, String path, String handlerClass, String handlerMethod, boolean sockJs) {
        this.kind = kind;
        this.path = path;
        this.handlerClass = handlerClass;
        this.handlerMethod = handlerMethod;
        this.sockJs = sockJs;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHandlerClass() {
        return handlerClass;
    }

    public void setHandlerClass(String handlerClass) {
        this.handlerClass = handlerClass;
    }

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }

    public boolean isSockJs() {
        return sockJs;
    }

    public void setSockJs(boolean sockJs) {
        this.sockJs = sockJs;
    }
}
