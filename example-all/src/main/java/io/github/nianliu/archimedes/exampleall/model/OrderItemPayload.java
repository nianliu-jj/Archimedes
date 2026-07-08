package io.github.nianliu.archimedes.exampleall.model;

import io.github.nianliu.archimedes.annotation.ApiField;
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
    @ApiField(value = "商品 ID", required = true)
    private Long productId;

    @ApiField(value = "购买数量")
    private int quantity;

    @ApiField(value = "成交单价")
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
