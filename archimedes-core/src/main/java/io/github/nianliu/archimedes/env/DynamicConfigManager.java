package io.github.nianliu.archimedes.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态配置管理器：配置热更新的写入口。
 * <p>维护一个名为 {@link #DYNAMIC_SOURCE_NAME} 的 {@link MapPropertySource}，
 * 惰性创建并 {@code addFirst} 挂到 Environment 最高优先级——写入其中的键值
 * 立即覆盖底层全部配置源；删除键即恢复底层原值。
 * <p>每次变更成功后依次触发：
 * <ol>
 *   <li>{@link ConfigurationPropertiesRebinder} 原地重绑定 prefix 命中的属性 Bean；</li>
 *   <li>发布 {@link ArchimedesConfigChangedEvent}（宿主监听该事件即可感知配置变化）。</li>
 * </ol>
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class DynamicConfigManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicConfigManager.class);

    /** 动态属性源名称（前端据此识别"动态覆盖"来源并高亮）。 */
    public static final String DYNAMIC_SOURCE_NAME = "archimedesDynamicConfig";

    private final ConfigurableEnvironment environment;
    private final ConfigurationPropertiesRebinder rebinder;
    private final ApplicationEventPublisher eventPublisher;
    /** 保护动态属性源的惰性创建与写入（读走 Environment 无锁）。 */
    private final Object lock = new Object();

    public DynamicConfigManager(ConfigurableEnvironment environment,
                                ConfigurationPropertiesRebinder rebinder,
                                ApplicationEventPublisher eventPublisher) {
        this.environment = environment;
        this.rebinder = rebinder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 更新或删除一个配置键的动态覆盖。
     *
     * @param key   配置键（调用方保证非空）
     * @param value 新值；null 表示删除该键的动态覆盖、恢复底层原值
     * @return 变更明细（新旧生效值、重绑定 Bean 列表）
     */
    public ConfigUpdateResult update(String key, String value) {
        String oldValue = environment.getProperty(key);
        boolean removed = (value == null);
        synchronized (lock) {
            Map<String, Object> dynamicMap = dynamicMap();
            if (removed) {
                dynamicMap.remove(key);
            } else {
                dynamicMap.put(key, value);
            }
        }
        String newValue = environment.getProperty(key);
        log.info("配置热更新: key={} 操作={} 生效值变化: [{}] -> [{}]",
                key, removed ? "删除覆盖" : "写入覆盖", oldValue, newValue);

        Set<String> changedKeys = Collections.singleton(key);
        // 先重绑定属性 Bean，再广播事件——监听者收到事件时 Bean 已是新值
        java.util.List<String> refreshedBeans = rebinder.rebind(changedKeys);
        publishEvents(changedKeys);
        return new ConfigUpdateResult(key, oldValue, newValue, removed, refreshedBeans);
    }

    /** 当前全部动态覆盖键（排序返回，便于前端稳定展示）。 */
    public Set<String> dynamicKeys() {
        PropertySource<?> source = environment.getPropertySources().get(DYNAMIC_SOURCE_NAME);
        if (!(source instanceof MapPropertySource)) {
            return Collections.emptySet();
        }
        return new TreeSet<>(((MapPropertySource) source).getSource().keySet());
    }

    /** 惰性获取/创建动态属性源的底层 Map（须在 lock 内调用）。 */
    private Map<String, Object> dynamicMap() {
        MutablePropertySources sources = environment.getPropertySources();
        PropertySource<?> existing = sources.get(DYNAMIC_SOURCE_NAME);
        if (existing instanceof MapPropertySource) {
            return ((MapPropertySource) existing).getSource();
        }
        // ConcurrentHashMap：Environment 读线程与写线程并发访问安全（值永不为 null）
        Map<String, Object> map = new ConcurrentHashMap<>();
        sources.addFirst(new MapPropertySource(DYNAMIC_SOURCE_NAME, map));
        log.info("已创建动态属性源 {} 并置于 Environment 最高优先级", DYNAMIC_SOURCE_NAME);
        return map;
    }

    /** 发布变更事件：宿主监听 {@link ArchimedesConfigChangedEvent} 即可感知配置变化。 */
    private void publishEvents(Set<String> changedKeys) {
        eventPublisher.publishEvent(new ArchimedesConfigChangedEvent(this, changedKeys));
    }
}
