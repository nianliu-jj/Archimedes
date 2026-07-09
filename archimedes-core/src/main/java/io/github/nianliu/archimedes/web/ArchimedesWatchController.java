package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiCatalog;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.FieldInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.RpcApiInfo;
import io.github.nianliu.archimedes.model.RpcMethodInfo;
import io.github.nianliu.archimedes.model.WsApiInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 热监听 SSE 推送端点：客户端建立 EventSource 连接后，服务端定时（或实时）
 * 比对接口契约 hash，有变化时主动推送最新的 {@link ApiCatalog}。
 *
 * <p>推送频率由 {@code archimedes.api.watch-interval-seconds} 控制：
 * <ul>
 *   <li>0（默认）= 实时：每秒检测一次</li>
 *   <li>&gt;0 = 按指定秒数间隔检测</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@RestController
public class ArchimedesWatchController {

    private static final Logger log = LoggerFactory.getLogger(ArchimedesWatchController.class);

    private final ArchimedesApiController apiController;
    private final ArchimedesApiProperties properties;

    /** 活跃的 SSE 连接列表（并发安全）。 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 上一次推送的契约内容签名（用于变化检测）。
     * <p>注意：不能用 {@code catalog.hashCode()}，因为 {@link ApiCatalog} 及其嵌套模型
     * 未重写 hashCode，用的是 JVM 身份哈希——而每次扫描都返回全新对象，身份哈希必然不同，
     * 会导致「每次检测都判定为变化」从而每秒向前端推送，前端每次重渲染都会折叠已展开的面板。
     * 故改用按内容拼装的确定性签名做比对，内容不变时签名恒等，不再误报变化。
     */
    private final AtomicReference<String> lastSignature = new AtomicReference<>(null);

    public ArchimedesWatchController(ArchimedesApiController apiController,
                                     ArchimedesApiProperties properties) {
        this.apiController = apiController;
        this.properties = properties;
        startWatcher();
    }

    /**
     * SSE 热监听端点：客户端建立连接后立即推送当前契约快照，
     * 之后由后台线程检测到变化时自动推送更新。
     */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/watch",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter watch() {
        // 30 分钟超时（浏览器 EventSource 超时后会自动重连）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.add(emitter);

        // 连接关闭/超时/错误时从列表移除，防止资源泄漏
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // 立即推送当前契约快照（客户端不必等第一个检测周期）
        try {
            ApiCatalog catalog = apiController.apis();
            emitter.send(SseEmitter.event().name("catalog").data(catalog, MediaType.APPLICATION_JSON));
            // 首个连接建立时以当前快照播种基线签名，使随后的首个检测周期不会误判为变化而重复推送
            // （仅 null→当前 生效；后续连接不会覆盖已有基线，避免抢占一个尚未推送的变更）
            lastSignature.compareAndSet(null, signature(catalog));
        } catch (IOException e) {
            log.debug("Archimedes: SSE 初始推送失败，移除连接", e);
            emitters.remove(emitter);
        }

        return emitter;
    }

    /** 启动后台定时检测线程（守护线程，应用关闭自动结束）。 */
    private void startWatcher() {
        int interval = properties.getWatchIntervalSeconds();
        int periodSeconds = interval > 0 ? interval : 1;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "archimedes-watch");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (emitters.isEmpty()) {
                return; // 无活跃连接时跳过扫描，节省资源
            }
            try {
                ApiCatalog catalog = apiController.apis();
                String currentSignature = signature(catalog);

                // 内容签名不变表示契约未更新，跳过推送（避免误报变化导致前端反复重渲染折叠面板）
                if (currentSignature.equals(lastSignature.getAndSet(currentSignature))) {
                    return;
                }

                // 向所有活跃连接推送更新
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.send(SseEmitter.event().name("catalog")
                                .data(catalog, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        emitters.remove(emitter);
                    }
                }
                log.debug("Archimedes: 契约变更已推送至 {} 个客户端", emitters.size());
            } catch (Exception e) {
                log.debug("Archimedes: watch 检测异常", e);
            }
        }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    /**
     * 按契约内容拼装确定性签名字符串，用于变化检测。
     * <p>遍历 REST/WebSocket/RPC 全部有意义的契约字段拼接（列表顺序由扫描器保证稳定，
     * map 用 TreeMap 排序去除遍历顺序不确定性）。内容相同则签名恒等，内容变化则签名不同——
     * 从而替代不可靠的对象身份哈希，只在契约真正变化时触发推送。
     */
    private String signature(ApiCatalog catalog) {
        StringBuilder sb = new StringBuilder(1024);
        // REST 契约
        for (ApiInfo a : catalog.getRestApis()) {
            sb.append("R|").append(a.getControllerClass()).append('#').append(a.getHandlerMethod())
                    .append('|').append(a.getHttpMethods()).append('|').append(a.getPaths())
                    .append('|').append(a.getReturnType()).append('|').append(a.getConsumes())
                    .append('|').append(a.getProduces()).append('|').append(a.isDeprecated())
                    .append('|').append(a.getSummary()).append('|').append(a.getOperationDescription())
                    .append('|').append(a.getTag()).append('|').append(a.getTagDescription()).append('|');
            if (a.getParams() != null) {
                for (ParamInfo p : a.getParams()) {
                    sb.append(p.getName()).append(':').append(p.getSource()).append(':').append(p.getType())
                            .append(':').append(p.isRequired()).append(':').append(p.getDescription())
                            .append(':').append(p.getExample()).append(':').append(map(p.getValidation())).append(',');
                }
            }
            appendField(sb.append("|req="), a.getRequestBodySchema());
            appendField(sb.append("|res="), a.getResponseSchema());
            for (io.github.nianliu.archimedes.model.ResponseInfo r : a.getResponses()) {
                sb.append("|RS").append(r.getCode()).append(':').append(r.getDescription())
                        .append(':').append(r.getType());
                appendField(sb.append(':'), r.getSchema());
            }
            sb.append('\n');
        }
        // WebSocket 契约
        for (WsApiInfo w : catalog.getWebSocketApis()) {
            sb.append("W|").append(w.getKind()).append('|').append(w.getPath()).append('|')
                    .append(w.getHandlerClass()).append('|').append(w.getHandlerMethod()).append('|')
                    .append(w.isSockJs()).append('|').append(w.getDescription()).append('\n');
        }
        // RPC 契约
        for (RpcApiInfo r : catalog.getRpcApis()) {
            sb.append("P|").append(r.getProtocol()).append('|').append(r.getServiceName()).append('|')
                    .append(r.getVersion()).append('|').append(r.getGroup()).append('|')
                    .append(map(r.getMetadata())).append('|').append(r.getDescription()).append('|');
            if (r.getMethods() != null) {
                for (RpcMethodInfo m : r.getMethods()) {
                    sb.append(m.getMethodName()).append('(').append(m.getParameterTypes()).append(')')
                            .append(m.getReturnType()).append(':').append(map(m.getMetadata()))
                            .append(':').append(m.getDescription()).append(',');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 递归拼装字段结构树签名（叶子递归到子节点，覆盖名称/类型/必填/说明/集合/枚举/校验）。 */
    private void appendField(StringBuilder sb, FieldInfo f) {
        if (f == null) {
            sb.append("null");
            return;
        }
        sb.append(f.getName()).append(':').append(f.getType()).append(':').append(f.isRequired())
                .append(':').append(f.getDescription()).append(':').append(f.isArray())
                .append(':').append(f.getEnumValues()).append(':').append(map(f.getValidation())).append('{');
        if (f.getChildren() != null) {
            for (FieldInfo c : f.getChildren()) {
                appendField(sb, c);
                sb.append(',');
            }
        }
        sb.append('}');
    }

    /** 将 map 转为按 key 排序的确定性字符串，消除遍历顺序不确定性；null 记为 "null"。 */
    private String map(Map<String, ?> m) {
        if (m == null || m.isEmpty()) {
            return "null";
        }
        return new TreeMap<String, Object>(m).toString();
    }
}
