package io.github.nianliu.archimedes.exampleall.controller;

import io.github.nianliu.archimedes.exampleall.model.CreateOrderRequest;
import io.github.nianliu.archimedes.exampleall.model.OrderItemPayload;
import io.github.nianliu.archimedes.exampleall.model.OrderResponse;
import io.swagger.v3.oas.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * REST 契约测试面：覆盖全部 HTTP 方法与参数形态
 * （@RequestParam/@PathVariable/@RequestBody/@RequestHeader、必填/可选、
 * @Parameter 参数说明、@Deprecated 标记、ResponseEntity 包装解包）。
 * 打开 http://localhost:8082/archimedes 的 REST Tab 即可对照契约与在线调试。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** 演示用固定明细（真实业务应查库） */
    private static final List<OrderItemPayload> SAMPLE_ITEMS = Collections.<OrderItemPayload>singletonList(sampleItem());

    /** GET + 可选查询参数 + @Parameter 说明 */
    @GetMapping
    public List<OrderResponse> list(
            @Parameter(description = "按订单状态过滤，缺省返回全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "分页大小，默认 10")
            @RequestParam(defaultValue = "10") int size) {
        log.info("list orders, status={}, size={}", status, size);
        return Arrays.<OrderResponse>asList(
                new OrderResponse("O-1001", "样例订单一", OrderResponse.Status.CREATED, SAMPLE_ITEMS),
                new OrderResponse("O-1002", "样例订单二", OrderResponse.Status.PAID, SAMPLE_ITEMS));
    }

    /** GET + 路径变量（调试面板路径变量输入框带说明悬浮） */
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> detail(
            @Parameter(description = "订单号，形如 O-1001")
            @PathVariable String orderNo) {
        log.info("query order detail, orderNo={}", orderNo);
        return ResponseEntity.<OrderResponse>ok(
                new OrderResponse(orderNo, "样例订单", OrderResponse.Status.CREATED, SAMPLE_ITEMS));
    }

    /** POST + 请求体（调试面板按 CreateOrderRequest 字段树自动预填示例 JSON）+ 请求头参数 */
    @PostMapping
    public OrderResponse create(
            @RequestBody CreateOrderRequest request,
            @Parameter(description = "幂等键，防止重复下单")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        log.info("create order, title={}, idempotencyKey={}", request.getTitle(), idempotencyKey);
        return new OrderResponse("O-" + UUID.randomUUID().toString().substring(0, 8),
                request.getTitle(), OrderResponse.Status.CREATED, request.getItems());
    }

    /** PUT：全量更新 */
    @PutMapping("/{orderNo}")
    public OrderResponse update(@PathVariable String orderNo, @RequestBody CreateOrderRequest request) {
        log.info("update order, orderNo={}", orderNo);
        return new OrderResponse(orderNo, request.getTitle(), OrderResponse.Status.PAID, request.getItems());
    }

    /** DELETE：void 返回 → 契约的 responseSchema 为空 */
    @DeleteMapping("/{orderNo}")
    public void cancel(@PathVariable String orderNo) {
        log.info("cancel order, orderNo={}", orderNo);
    }

    /** 弃用端点：契约带 deprecated 标记，UI 中删除线展示 */
    @Deprecated
    @GetMapping("/legacy-count")
    public long legacyCount() {
        return 2L;
    }

    private static OrderItemPayload sampleItem() {
        OrderItemPayload item = new OrderItemPayload();
        item.setProductId(9527L);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("19.90"));
        return item;
    }
}
