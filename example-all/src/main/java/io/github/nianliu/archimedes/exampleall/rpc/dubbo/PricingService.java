package io.github.nianliu.archimedes.exampleall.rpc.dubbo;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;

import java.math.BigDecimal;

/**
 * Dubbo 服务接口：契约中的 serviceName = 本接口全限定名。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@ApiModule(name = "定价服务", description = "Dubbo 询价服务，按商品编码与数量返回报价")
public interface PricingService {

    /** 询价：方法签名（入参/返回类型）会被完整提取进契约 */
    @ApiDoc(summary = "询价", description = "按商品编码与数量计算总价")
    BigDecimal quote(String productCode, int quantity);
}
