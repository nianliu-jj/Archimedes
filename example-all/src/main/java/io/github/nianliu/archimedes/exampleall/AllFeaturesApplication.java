package io.github.nianliu.archimedes.exampleall;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 全功能测试应用入口：仅引入 archimedes-spring-boot-3-starter 即获得
 * 契约扫描（/archimedes/apis + UI）与链路追踪/日志查询全部能力——
 * 除演示统一响应包装的可选 opt-in（archimedes.api.response-wrapper.*）外，
 * 本应用不含其它 Archimedes 配置，验证"引入即用"。
 *
 * <p>覆盖面：REST（含 schema 提取）、WebSocket 三形态、Dubbo/gRPC/SOFA-TR/tRPC
 * 四协议、@Async 与自定义线程池的跨线程 traceId 传递、按 traceId 日志查询、
 * logback 兜底（本应用刻意不放任何 logback 配置文件）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@EnableAsync                // 开启 @Async：验证异步线程的 traceId 自动传递
@EnableDubbo                // 扫描 @DubboService：Dubbo provider 契约扫描的数据源
@SpringBootApplication
public class AllFeaturesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AllFeaturesApplication.class, args);
    }
}
