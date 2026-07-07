package io.github.nianliu.archimedes.web;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.env.ConfigManagementProperties;
import io.github.nianliu.archimedes.env.ConfigSnapshot;
import io.github.nianliu.archimedes.env.ConfigUpdateResult;
import io.github.nianliu.archimedes.env.DynamicConfigManager;
import io.github.nianliu.archimedes.env.EnvironmentConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置中心端点：
 * <ul>
 *   <li>{@code GET {base-path}/config}：全量配置查询（按属性源分组、敏感值脱敏、
 *       附热更新开关状态与动态覆盖键列表）；</li>
 *   <li>{@code POST {base-path}/config/update}：热更新（body {@code {key, value}}，
 *       value 为 null/缺省表示删除动态覆盖恢复原值）。</li>
 * </ul>
 * 纯注解式控制器，零 servlet 依赖——Servlet 与 WebFlux 两栈复用同一实例。
 * 路径位于 {@code {base-path}} 下，天然被契约扫描的 base-path 排除规则覆盖。
 *
 * @author nianliu-jj
 * @since 2026-07-07
 */
@RestController
public class ArchimedesConfigController {

    private static final Logger log = LoggerFactory.getLogger(ArchimedesConfigController.class);

    private final EnvironmentConfigService configService;
    private final DynamicConfigManager dynamicConfigManager;
    private final ConfigManagementProperties properties;

    public ArchimedesConfigController(EnvironmentConfigService configService,
                                      DynamicConfigManager dynamicConfigManager,
                                      ConfigManagementProperties properties) {
        this.configService = configService;
        this.dynamicConfigManager = dynamicConfigManager;
        this.properties = properties;
    }

    /** 全量配置查询：属性源按 Environment 优先级排序，敏感值已脱敏。 */
    @GetMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/config",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ConfigSnapshot config() {
        return new ConfigSnapshot(properties.isHotRefreshEnabled(),
                new ArrayList<>(dynamicConfigManager.dynamicKeys()),
                configService.listPropertySources());
    }

    /**
     * 配置热更新：写入/删除动态覆盖并触发重绑定与事件。
     * <p>语义：key 空 → 400；hot-refresh-enabled=false → 403（不产生任何变更）；
     * 成功 → 200 + 变更明细（新旧值按敏感规则脱敏）。
     */
    @PostMapping(value = "${archimedes.api.base-path:" + ArchimedesApiProperties.DEFAULT_BASE_PATH + "}/config/update",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> update(@RequestBody(required = false) Map<String, String> body) {
        String key = body == null ? null : trimToNull(body.get("key"));
        if (key == null) {
            log.warn("配置热更新请求被拒绝: key 为空");
            return error(HttpStatus.BAD_REQUEST, "key 不能为空");
        }
        if (!properties.isHotRefreshEnabled()) {
            log.warn("配置热更新请求被拒绝: archimedes.config.hot-refresh-enabled=false (key={})", key);
            return error(HttpStatus.FORBIDDEN, "配置热更新已关闭（archimedes.config.hot-refresh-enabled=false）");
        }
        ConfigUpdateResult result = dynamicConfigManager.update(key, body.get("value"));

        // 新旧生效值与查询端点遵循同一脱敏规则，避免敏感值经 update 响应泄露
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("key", result.getKey());
        resp.put("oldValue", configService.maskIfSensitive(key, result.getOldValue()));
        resp.put("newValue", configService.maskIfSensitive(key, result.getNewValue()));
        resp.put("removed", result.isRemoved());
        resp.put("refreshedBeans", result.getRefreshedBeans());
        return ResponseEntity.ok(resp);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        // 用 status(int) 重载：Spring 5.3 与 6.x 字节码签名一致；
        // status(HttpStatus) 在 Spring 6 已改为 status(HttpStatusCode)，5.3 编译产物跨版本会 NoSuchMethodError
        return ResponseEntity.status(status.value()).body(body);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
