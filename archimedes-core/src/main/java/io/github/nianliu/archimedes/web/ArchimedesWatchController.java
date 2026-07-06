package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 上一次推送的 catalog hash（用于变化检测）。 */
    private final AtomicInteger lastHash = new AtomicInteger(0);

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
                int currentHash = catalog.hashCode();

                // hash 不变表示契约未更新，跳过推送
                if (lastHash.getAndSet(currentHash) == currentHash) {
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
}
