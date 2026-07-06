package io.github.nianliu.archimedes.exampleall.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 订单明细项：作为 {@link CreateOrderRequest} 的嵌套元素类型，
 * 验证字段树的多层展开（items[].productId 等）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class OrderItemPayload {

    @NotNull
    @Schema(description = "商品 ID")
    private Long productId;

    @Schema(description = "购买数量")
    private int quantity;

    @Schema(description = "成交单价")
    private BigDecimal price;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
