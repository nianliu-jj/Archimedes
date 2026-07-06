package io.github.nianliu.archimedes.exampleall.rpc.sofa;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import org.springframework.stereotype.Component;

/**
 * SOFARPC-TR 契约演示服务：@SofaService（桩注解，同真实 FQCN）标注的 Bean
 * 被 Archimedes 反射扫描——期望契约：protocol=SOFA_TR、
 * serviceName=GreetingFacade 全限定名、metadata.uniqueId=demo、metadata.bindings=tr。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Component
@SofaService(interfaceType = GreetingFacade.class, uniqueId = "demo",
        bindings = @SofaServiceBinding(bindingType = "tr"))
public class SofaGreetingImpl implements GreetingFacade {

    @Override
    public String greet(String name) {
        return "sofa hi, " + name;
    }
}
