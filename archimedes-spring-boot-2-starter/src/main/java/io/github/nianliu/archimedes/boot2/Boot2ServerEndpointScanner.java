package io.github.nianliu.archimedes.boot2;

import io.github.nianliu.archimedes.model.WsApiInfo;
import io.github.nianliu.archimedes.scanner.schema.TypeSchemaResolver;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import javax.websocket.server.ServerEndpoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 扫描注册为 Spring Bean 的 javax @ServerEndpoint 端点（配合 ServerEndpointExporter 的标准用法）。
 * 容器 SCI 直接注册、绕过 Spring 的端点不在覆盖范围内。
 */
public class Boot2ServerEndpointScanner implements WebSocketApiContributor {

    private final ApplicationContext applicationContext;

    public Boot2ServerEndpointScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<WsApiInfo> contribute() {
        List<WsApiInfo> result = new ArrayList<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ServerEndpoint.class);
        for (Object bean : beans.values()) {
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
        result.sort(Comparator.comparing(WsApiInfo::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}
