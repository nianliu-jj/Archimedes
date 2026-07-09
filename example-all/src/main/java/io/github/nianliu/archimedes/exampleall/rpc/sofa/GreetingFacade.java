package io.github.nianliu.archimedes.exampleall.rpc.sofa;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;

/**
 * SOFA 服务接口（契约 serviceName 的来源）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@ApiModule(name = "SOFA 问候服务", description = "SOFARPC-TR 问候门面，按名称返回问候语")
public interface GreetingFacade {

    @ApiDoc(summary = "问候", description = "按名称返回一句问候语")
    String greet(String name);
}
