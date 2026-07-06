package io.github.nianliu.archimedes.exampleall.rpc.dubbo;

import org.apache.dubbo.config.annotation.DubboService;

import java.math.BigDecimal;

/**
 * Dubbo provider 实现：@DubboService 导出（registry N/A 本地导出，见 application.yml），
 * Archimedes 通过容器内 ServiceBean 自省提取契约——
 * 期望契约：protocol=DUBBO、version=1.0.0、group=pricing、方法 quote(String,int)。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@DubboService(version = "1.0.0", group = "pricing")
public class PricingServiceImpl implements PricingService {

    /** 演示实现：单价 9.9 * 数量 */
    @Override
    public BigDecimal quote(String productCode, int quantity) {
        return new BigDecimal("9.9").multiply(BigDecimal.valueOf(quantity));
    }
}
