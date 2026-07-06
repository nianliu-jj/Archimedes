package io.github.nianliu.archimedes.exampleall.rpc.dubbo;

import java.math.BigDecimal;

/**
 * Dubbo 服务接口：契约中的 serviceName = 本接口全限定名。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public interface PricingService {

    /** 询价：方法签名（入参/返回类型）会被完整提取进契约 */
    BigDecimal quote(String productCode, int quantity);
}
