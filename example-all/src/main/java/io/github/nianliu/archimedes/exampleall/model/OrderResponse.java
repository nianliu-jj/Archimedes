package io.github.nianliu.archimedes.exampleall.model;

import io.github.nianliu.archimedes.annotation.ApiField;

import java.util.List;

/**
 * 订单响应体：UI 调试面板"响应字段"表的数据来源
 * （用户发送请求前即可了解响应结构与字段含义）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public class OrderResponse {

    @ApiField(value = "订单号")
    private String orderNo;

    @ApiField(value = "订单标题")
    private String title;

    @ApiField(value = "订单状态")
    private Status status;

    @ApiField(value = "订单明细")
    private List<OrderItemPayload> items;

    /** 订单状态枚举：契约说明自动补可选值 */
    public enum Status { CREATED, PAID, SHIPPED, CLOSED }

    public OrderResponse() {
    }

    public OrderResponse(String orderNo, String title, Status status, List<OrderItemPayload> items) {
        this.orderNo = orderNo;
        this.title = title;
        this.status = status;
        this.items = items;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<OrderItemPayload> getItems() {
        return items;
    }

    public void setItems(List<OrderItemPayload> items) {
        this.items = items;
    }
}
