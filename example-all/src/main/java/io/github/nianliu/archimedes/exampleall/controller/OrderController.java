package io.github.nianliu.archimedes.exampleall.controller;

import io.github.nianliu.archimedes.exampleall.model.CreateOrderRequest;
import io.github.nianliu.archimedes.exampleall.model.OrderItemPayload;
import io.github.nianliu.archimedes.exampleall.model.OrderResponse;
import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
import io.github.nianliu.archimedes.annotation.NoApiWrapper;
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
 * @ApiParam 参数说明、@Deprecated 标记、ResponseEntity 包装解包）。
 * 打开 http://localhost:8082/archimedes 的 REST Tab 即可对照契约与在线调试。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@RestController
@RequestMapping("/api/orders")
@ApiModule(name = "订单管理", description = "订单的增删改查演示，覆盖全部 HTTP 方法与参数形态")
// 统一响应包装体演示：运行时包装 advice（GlobalResponseAdvice）仅作用于 wrapper 演示包，
// 本控制器实际返回裸类型；标 @NoApiWrapper 使契约 responseSchema 与真实返回保持一致（不套 ResultVo）。
@NoApiWrapper
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** 演示用固定明细（真实业务应查库） */
    private static final List<OrderItemPayload> SAMPLE_ITEMS = Collections.<OrderItemPayload>singletonList(sampleItem());

    /** GET + 可选查询参数 + @ApiParam 说明 */
    @ApiDoc(summary = "查询订单列表", description = "支持按状态过滤与分页，缺省返回全部订单")
    @GetMapping
    public List<OrderResponse> list(
            @ApiParam(value = "按订单状态过滤，缺省返回全部", example = "PAID")
            @RequestParam(required = false) String status,
            @ApiParam(value = "分页大小，默认 10", example = "20")
            @RequestParam(defaultValue = "10") int size) {
        log.info("list orders, status={}, size={}", status, size);
        return Arrays.<OrderResponse>asList(
                new OrderResponse("O-1001", "样例订单一", OrderResponse.Status.CREATED, SAMPLE_ITEMS),
                new OrderResponse("O-1002", "样例订单二", OrderResponse.Status.PAID, SAMPLE_ITEMS));
    }

    /** GET + 路径变量（调试面板路径变量输入框带说明悬浮） */
    @ApiDoc(summary = "查询订单详情", description = "按订单号返回单个订单，演示 ResponseEntity 包装解包")
    @ApiResponse(code = 200, description = "命中订单", type = OrderResponse.class)
    @ApiResponse(code = 404, description = "订单不存在")
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderResponse> detail(
            @ApiParam(value = "订单号，形如 O-1001", example = "O-1001")
            @PathVariable String orderNo) {
        log.info("query order detail, orderNo={}", orderNo);
        return ResponseEntity.<OrderResponse>ok(
                new OrderResponse(orderNo, "样例订单", OrderResponse.Status.CREATED, SAMPLE_ITEMS));
    }

    /** POST + 请求体（调试面板按 CreateOrderRequest 字段树自动预填示例 JSON）+ 请求头参数 */
    @ApiDoc(summary = "创建订单", description = "提交订单请求体，返回生成的订单号；请求体字段结构在调试面板自动预填")
    @PostMapping
    public OrderResponse create(
            @RequestBody CreateOrderRequest request,
            @ApiParam(value = "幂等键，防止重复下单", example = "idem-20260708-001")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        log.info("create order, title={}, idempotencyKey={}", request.getTitle(), idempotencyKey);
        return new OrderResponse("O-" + UUID.randomUUID().toString().substring(0, 8),
                request.getTitle(), OrderResponse.Status.CREATED, request.getItems());
    }

    /** PUT：全量更新 */
    @ApiDoc(summary = "更新订单", description = "按订单号全量更新订单内容")
    @PutMapping("/{orderNo}")
    public OrderResponse update(@PathVariable String orderNo, @RequestBody CreateOrderRequest request) {
        log.info("update order, orderNo={}", orderNo);
        return new OrderResponse(orderNo, request.getTitle(), OrderResponse.Status.PAID, request.getItems());
    }

    /** DELETE：void 返回 → 契约的 responseSchema 为空 */
    @ApiDoc(summary = "取消订单", description = "按订单号取消，无响应体（responseSchema 为空）")
    @DeleteMapping("/{orderNo}")
    public void cancel(@PathVariable String orderNo) {
        log.info("cancel order, orderNo={}", orderNo);
    }

    /** 弃用端点：契约带 deprecated 标记，UI 中删除线展示 */
    @ApiDoc(summary = "统计订单数（已弃用）", deprecated = true)
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
