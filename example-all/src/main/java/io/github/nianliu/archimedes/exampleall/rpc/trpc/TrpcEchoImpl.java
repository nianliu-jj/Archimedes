package io.github.nianliu.archimedes.exampleall.rpc.trpc;

import com.tencent.trpc.spring.annotation.TRpcService;
import org.springframework.stereotype.Component;

/**
 * 腾讯 tRPC 契约演示服务：@TRpcService（桩注解，同真实 FQCN）标注的 Bean
 * 被 Archimedes 反射扫描——期望契约：protocol=TRPC、
 * serviceName=EchoApi 全限定名（唯一实现接口回退）、version=v1、group=g1。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@Component
@TRpcService(name = "demo.trpc.Echo", version = "v1", group = "g1")
public class TrpcEchoImpl implements EchoApi {

    @Override
    public String echo(String message) {
        return "trpc echo: " + message;
    }
}
