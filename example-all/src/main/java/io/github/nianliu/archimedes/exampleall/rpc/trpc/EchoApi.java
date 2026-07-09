package io.github.nianliu.archimedes.exampleall.rpc.trpc;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;

/**
 * tRPC 服务接口（契约 serviceName 来源：唯一实现接口）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
@ApiModule(name = "tRPC 回显服务", description = "腾讯 tRPC 回显服务，原样返回入参消息")
public interface EchoApi {

    @ApiDoc(summary = "回显", description = "原样返回客户端发送的消息")
    String echo(String message);
}
