package io.github.nianliu.archimedes.exampleall;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nianliu.archimedes.exampleall.rpc.grpc.ManualGreeterGrpcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全功能端到端测试：一个测试类覆盖 Archimedes 全部能力——
 * REST 契约（含 schema）、WebSocket 三形态、Dubbo/gRPC/SOFA-TR/tRPC
 * 四协议、trace/跨线程传递、日志查询、UI 结构。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
@SpringBootTest(classes = AllFeaturesApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dubbo.application.qos-enable=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.port=-1"
        })
class AllFeaturesEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    /* ---------- 公共工具 ---------- */

    @SuppressWarnings("unchecked")
    private Map<String, Object> catalog() throws Exception {
        String body = rest.getForEntity("/archimedes/apis", String.class).getBody();
        return mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> restApis() throws Exception {
        return (List<Map<String, Object>>) catalog().get("restApis");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> wsApis() throws Exception {
        return (List<Map<String, Object>>) catalog().get("webSocketApis");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rpcApis() throws Exception {
        return (List<Map<String, Object>>) catalog().get("rpcApis");
    }

    /* ========== 1. REST 契约 + Schema ========== */

    @Test
    @SuppressWarnings("unchecked")
    void restContractWithSchemaIsComplete() throws Exception {
        List<Map<String, Object>> apis = restApis();

        // POST /api/orders 在列
        Map<String, Object> createOrder = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/orders")
                        && ((List<String>) a.get("httpMethods")).contains("POST"))
                .findFirst().orElseThrow();

        // requestBodySchema 字段树：title 必填 + 说明、order_channel（@JsonProperty 改名）、items 集合
        Map<String, Object> reqSchema = (Map<String, Object>) createOrder.get("requestBodySchema");
        assertThat(reqSchema).isNotNull();
        assertThat(reqSchema.get("type")).isEqualTo("CreateOrderRequest");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) reqSchema.get("children");
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.get("name")).isEqualTo("title");
            assertThat(f.get("description")).isEqualTo("订单标题");
            assertThat(f.get("required")).isEqualTo(true);
        });
        assertThat(fields).anySatisfy(f -> assertThat(f.get("name")).isEqualTo("order_channel"));
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.get("name")).isEqualTo("items");
            assertThat(f.get("array")).isEqualTo(true);
        });
        // @JsonIgnore 字段被剔除
        assertThat(fields).noneMatch(f -> "internalToken".equals(f.get("name")));

        // responseSchema 解包后为 OrderResponse
        Map<String, Object> respSchema = (Map<String, Object>) createOrder.get("responseSchema");
        assertThat(respSchema).isNotNull();
        assertThat(respSchema.get("type")).isEqualTo("OrderResponse");

        // 参数说明：X-Idempotency-Key 头的 description 非空
        List<Map<String, Object>> params = (List<Map<String, Object>>) createOrder.get("params");
        assertThat(params).anySatisfy(p -> {
            assertThat(p.get("name")).isEqualTo("X-Idempotency-Key");
            assertThat((String) p.get("description")).contains("幂等");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailDeclaresResponses() throws Exception {
        // detail 端点（/api/orders/{orderNo}）声明了 @ApiResponse(200) 与 @ApiResponse(404)，
        // 校验其被扫描进契约的 responses 区块，覆盖响应描述注解的端到端链路。
        List<Map<String, Object>> apis = restApis();
        Map<String, Object> detail = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/orders/{orderNo}")
                        && ((List<String>) a.get("httpMethods")).contains("GET"))
                .findFirst().orElseThrow();
        List<Map<String, Object>> responses = (List<Map<String, Object>>) detail.get("responses");
        assertThat(responses).extracting(r -> r.get("code")).contains(200, 404);

        // FIX1 端到端守卫：orderNo 是 @PathVariable（绑定必填）且标了 @ApiParam 但未写 required，
        // @ApiParam 只能上调、不得把必填降为可选，故契约中仍应 required==true。
        List<Map<String, Object>> params = (List<Map<String, Object>>) detail.get("params");
        assertThat(params).anySatisfy(p -> {
            assertThat(p.get("name")).isEqualTo("orderNo");
            assertThat(p.get("required")).isEqualTo(true);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void restExcludesSelfAndError() throws Exception {
        List<Map<String, Object>> apis = restApis();
        assertThat(apis).noneSatisfy(a ->
                assertThat((List<String>) a.get("paths")).contains("/archimedes/apis"));
        assertThat(apis).noneSatisfy(a ->
                assertThat((List<String>) a.get("paths")).contains("/error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deprecatedEndpointIsMarked() throws Exception {
        List<Map<String, Object>> apis = restApis();
        Map<String, Object> legacy = apis.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/orders/legacy-count"))
                .findFirst().orElseThrow();
        assertThat(legacy.get("deprecated")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrappedEndpointShowsResultVoSchema() throws Exception {
        List<Map<String, Object>> rest = restApis();
        Map<String, Object> items = rest.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/wrapper/items"))
                .findFirst().orElseThrow();
        Map<String, Object> schema = (Map<String, Object>) items.get("responseSchema");
        assertThat(schema.get("type")).isEqualTo("ResultVo");
        List<Map<String, Object>> children = (List<Map<String, Object>>) schema.get("children");
        assertThat(children).extracting(c -> c.get("name")).contains("code", "msg", "data");
        Map<String, Object> data = children.stream()
                .filter(c -> c.get("name").equals("data")).findFirst().orElseThrow();
        assertThat(data.get("type")).isEqualTo("Item");
        assertThat(data.get("array")).isEqualTo(true);

        // @NoApiWrapper 端点：responseSchema 保持裸 Item（不套 ResultVo）
        Map<String, Object> raw = rest.stream()
                .filter(a -> ((List<String>) a.get("paths")).contains("/api/wrapper/items-raw"))
                .findFirst().orElseThrow();
        Map<String, Object> rawSchema = (Map<String, Object>) raw.get("responseSchema");
        assertThat(rawSchema.get("type")).isEqualTo("Item");
    }

    /* ========== 2. WebSocket 三形态 ========== */

    @Test
    void webSocketThreeFormsScanned() throws Exception {
        List<Map<String, Object>> ws = wsApis();

        // 形态一：@ServerEndpoint
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("SERVER_ENDPOINT");
            assertThat(w.get("path")).isEqualTo("/ws/native/{room}");
        });
        // 形态二：Spring handler（无 SockJS）
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("HANDLER");
            assertThat(w.get("path")).isEqualTo("/ws/echo");
            assertThat(w.get("sockJs")).isEqualTo(false);
        });
        // 形态二：Spring handler（有 SockJS）
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("path")).isEqualTo("/ws/echo-sockjs");
            assertThat(w.get("sockJs")).isEqualTo(true);
        });
        // 形态三：STOMP 握手端点
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_ENDPOINT");
            assertThat(w.get("path")).isEqualTo("/ws/stomp");
        });
        // 形态三：STOMP @MessageMapping
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_MESSAGE");
            assertThat(w.get("path")).isEqualTo("/chat.send");
        });
        // 形态三：STOMP @SubscribeMapping
        assertThat(ws).anySatisfy(w -> {
            assertThat(w.get("kind")).isEqualTo("STOMP_SUBSCRIBE");
            assertThat(w.get("path")).isEqualTo("/chat.history");
        });
    }

    /* ========== 3. RPC 四协议 ========== */

    @Test
    @SuppressWarnings("unchecked")
    void dubboProviderScanned() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("DUBBO");
            assertThat((String) api.get("serviceName")).endsWith("PricingService");
            assertThat(api.get("version")).isEqualTo("1.0.0");
            assertThat(api.get("group")).isEqualTo("pricing");
            List<Map<String, Object>> methods = (List<Map<String, Object>>) api.get("methods");
            assertThat(methods).anySatisfy(m -> assertThat(m.get("methodName")).isEqualTo("quote"));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void grpcServiceScanned() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("GRPC");
            assertThat(api.get("serviceName")).isEqualTo(ManualGreeterGrpcService.SERVICE_NAME);
            List<Map<String, Object>> methods = (List<Map<String, Object>>) api.get("methods");
            assertThat(methods).anySatisfy(m -> {
                assertThat(m.get("methodName")).isEqualTo("SayHello");
                Map<String, Object> meta = (Map<String, Object>) m.get("metadata");
                assertThat(meta.get("grpcMethodType")).isEqualTo("UNARY");
            });
            assertThat(methods).anySatisfy(m -> {
                assertThat(m.get("methodName")).isEqualTo("StreamGreetings");
                Map<String, Object> meta = (Map<String, Object>) m.get("metadata");
                assertThat(meta.get("grpcMethodType")).isEqualTo("SERVER_STREAMING");
            });
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void sofaTrServiceScanned() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("SOFA_TR");
            assertThat((String) api.get("serviceName")).endsWith("GreetingFacade");
            Map<String, Object> meta = (Map<String, Object>) api.get("metadata");
            assertThat(meta.get("uniqueId")).isEqualTo("demo");
            assertThat(meta.get("bindings")).isEqualTo("tr");
        });
    }

    @Test
    void trpcServiceScanned() throws Exception {
        assertThat(rpcApis()).anySatisfy(api -> {
            assertThat(api.get("protocol")).isEqualTo("TRPC");
            assertThat((String) api.get("serviceName")).endsWith("EchoApi");
            assertThat(api.get("version")).isEqualTo("v1");
            assertThat(api.get("group")).isEqualTo("g1");
        });
    }

    /* ========== 4. Trace + 跨线程 + 日志查询 ========== */

    @Test
    void traceIdInResponseHeader() {
        ResponseEntity<String> resp = rest.getForEntity("/trace/sync", String.class);
        assertThat(resp.getHeaders().getFirst("X-Trace-Id")).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void asyncTraceIdPropagatedAndLogsQueryable() throws Exception {
        // 触发 @Async 跨线程路径
        ResponseEntity<String> resp = rest.getForEntity("/trace/async", String.class);
        String traceId = resp.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).isNotEmpty();

        // 等异步线程日志落入
        Thread.sleep(1000);

        // 查询该 traceId 的日志
        String logBody = rest.getForEntity(
                "/archimedes/logs/trace/" + traceId + "?page=1&size=100", String.class).getBody();
        Map<String, Object> logResult = mapper.readValue(logBody, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> logs = (List<Map<String, Object>>) logResult.get("logs");

        // 至少两条：请求线程 + all-async-* 线程
        assertThat(logs.size()).isGreaterThanOrEqualTo(2);
        assertThat(logs).anySatisfy(l ->
                assertThat((String) l.get("thread")).startsWith("http-nio-"));
        assertThat(logs).anySatisfy(l ->
                assertThat((String) l.get("thread")).startsWith("all-async-"));
    }

    @Test
    void traceCurrentEndpointReturnsTraceId() {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes/trace/current", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotEmpty();
    }

    /* ========== 5. UI 结构 ========== */

    @Test
    void uiServesWithTabsAndSchemaSupport() {
        ResponseEntity<String> resp = rest.getForEntity("/archimedes", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        String body = resp.getBody();
        assertThat(body).contains("/archimedes/apis");
        assertThat(body).doesNotContain("__ARCHIMEDES_API_URL__");
        // Tab 化结构
        assertThat(body).contains("id=\"tabs\"");
        // Schema 辅助标记
        assertThat(body).contains("exampleFromSchema");
        assertThat(body).contains("Request Fields");
        assertThat(body).contains("Response Fields");
    }

    /* ========== 6. 分组结构完整性 ========== */

    @Test
    void catalogGroupStructureComplete() throws Exception {
        Map<String, Object> cat = catalog();
        assertThat(cat).containsKeys("restApis", "webSocketApis", "rpcApis");
        assertThat((List<?>) cat.get("restApis")).isNotEmpty();
        assertThat((List<?>) cat.get("webSocketApis")).isNotEmpty();
        assertThat((List<?>) cat.get("rpcApis")).isNotEmpty();
    }
}
