package io.github.nianliu.archimedes.exampleall.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SQL 监控演示控制器：经 JdbcTemplate 产生真实 SQL 流量
 * （查询/单参查询/插入/失败 SQL），供控制台 DB Tab 观察
 * 连接池指标、SQL 统计、慢 SQL 与 traceId 关联。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
@RestController
public class OrderDbController {

    private final JdbcTemplate jdbc;

    public OrderDbController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 全量订单查询（QUERY：ResultSet 行数计数演示）。 */
    @GetMapping("/api/db/orders")
    public List<Map<String, Object>> listOrders() {
        return jdbc.queryForList("SELECT * FROM orders_db ORDER BY id");
    }

    /** 单参查询（PreparedStatement 绑定参数捕获演示）。 */
    @GetMapping("/api/db/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable int id) {
        return jdbc.queryForMap("SELECT * FROM orders_db WHERE id = ?", id);
    }

    /** 插入订单（UPDATE：影响行数演示）。 */
    @PostMapping("/api/db/orders")
    public Map<String, Object> createOrder(@RequestParam int id,
                                           @RequestParam String item,
                                           @RequestParam double amount) {
        int affected = jdbc.update("MERGE INTO orders_db KEY(id) VALUES (?, ?, ?)", id, item, amount);
        return Map.of("affected", affected);
    }

    /** 故意执行失败 SQL（异常记录与 errorCount 演示）。 */
    @GetMapping("/api/db/fail")
    public Map<String, Object> failingSql() {
        try {
            jdbc.execute("SELECT * FROM table_not_exist");
        } catch (Exception ex) {
            return Map.of("captured", true, "error", String.valueOf(ex.getMessage()));
        }
        return Map.of("captured", false);
    }
}
