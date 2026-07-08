package io.github.nianliu.archimedes.env;

import org.springframework.context.ApplicationEvent;

import java.util.Set;

/**
 * 配置热更新完成后发布的应用事件：携带本次变更的配置键集合。
 * <p>宿主可注册 {@code ApplicationListener<ArchimedesConfigChangedEvent>} 监听配置变化
 * （例如刷新自维护的静态配置缓存，或转发给宿主自有的配置刷新机制）。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
public class ArchimedesConfigChangedEvent extends ApplicationEvent {

    /** 本次变更（新增/修改/删除覆盖）的配置键集合。 */
    private final Set<String> keys;

    public ArchimedesConfigChangedEvent(Object source, Set<String> keys) {
        super(source);
        this.keys = keys;
    }

    public Set<String> getKeys() {
        return keys;
    }
}
