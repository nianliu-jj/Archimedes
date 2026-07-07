-- SQL 监控演示表结构（Boot 启动时对内嵌 H2 自动执行）
CREATE TABLE IF NOT EXISTS orders_db (
    id   INT PRIMARY KEY,
    item VARCHAR(64)  NOT NULL,
    amount DECIMAL(10, 2) NOT NULL
);
