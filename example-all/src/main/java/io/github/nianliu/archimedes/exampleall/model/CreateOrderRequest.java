package io.github.nianliu.archimedes.exampleall.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nianliu.archimedes.annotation.ApiField;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 下单请求体：集中演示契约 schema 提取的全部注解形态——
 * 自有 @ApiField 说明/必填、validation @NotNull 必填、@JsonProperty 改名、
 * @JsonIgnore 剔除、枚举可选值、嵌套对象与集合。
 * 在 UI 调试面板中可看到：按本类字段树自动生成的示例 JSON 与"请求字段"说明表。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class CreateOrderRequest {

    /** 必填 + 说明：UI 字段表展示"订单标题"并带 * 号 */
    @NotNull
    @ApiField(value = "订单标题", required = true)
    private String title;

    /** @JsonProperty 改名：契约中字段名为 order_channel 而非 orderChannel */
    @JsonProperty("order_channel")
    @ApiField(value = "下单渠道")
    private OrderChannel orderChannel;

    /** 嵌套集合：字段表中 items 带 [] 标记，元素字段缩进展示 */
    @ApiField(value = "订单明细列表")
    private List<OrderItemPayload> items;

    /** 无注解字段：说明为空、类型照常提取 */
    private String remark;

    /** @JsonIgnore：不参与序列化，契约字段树中被剔除 */
    @JsonIgnore
    private String internalToken;

    /** 下单渠道枚举：契约说明自动补"枚举: APP / WEB / MINI_PROGRAM" */
    public enum OrderChannel { APP, WEB, MINI_PROGRAM }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OrderChannel getOrderChannel() {
        return orderChannel;
    }

    public void setOrderChannel(OrderChannel orderChannel) {
        this.orderChannel = orderChannel;
    }

    public List<OrderItemPayload> getItems() {
        return items;
    }

    public void setItems(List<OrderItemPayload> items) {
        this.items = items;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
