package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描注册为 Spring Bean 的 jakarta @ServerEndpoint 端点（配合 ServerEndpointExporter 的标准用法）。
 * 容器 SCI 直接注册、绕过 Spring 的端点不在覆盖范围内。
 * 作为 {@link WebSocketApiContributor} 实现向内置 API 控制器贡献 WebSocket 契约（SB3 用 jakarta 命名空间）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class Boot3ServerEndpointScanner implements WebSocketApiContributor {

    private final ApplicationContext applicationContext;

    public Boot3ServerEndpointScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 遍历带 @ServerEndpoint 的 Bean，取其 value（路径）与真实用户类名，汇成排序后的 WS 契约列表。 */
    @Override
    public List<WsApiInfo> contribute() {
        List<WsApiInfo> result = new ArrayList<>();
        // 按注解筛出所有 @ServerEndpoint Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ServerEndpoint.class);
        for (Object bean : beans.values()) {
            // 剥离 CGLIB/代理包装，拿到用户原始类以正确读取注解
            Class<?> userClass = ClassUtils.getUserClass(bean);
            ServerEndpoint annotation = userClass.getAnnotation(ServerEndpoint.class);
            if (annotation != null) {
                WsApiInfo info = new WsApiInfo(WsApiInfo.KIND_SERVER_ENDPOINT, annotation.value(),
                        userClass.getName(), null, false);
                // @ServerEndpoint 无固定业务方法可标 @ApiDoc，故端点描述取类上的 @ApiModule#description
                info.setDescription(TypeSchemaResolver.tagDescriptionOrNull(userClass.getAnnotations()));
                result.add(info);
            }
        }
        // 按路径排序（null 置后），保证契约展示顺序稳定
        result.sort(Comparator.comparing(WsApiInfo::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
