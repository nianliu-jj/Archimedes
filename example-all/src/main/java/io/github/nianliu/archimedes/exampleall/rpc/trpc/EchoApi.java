package io.github.nianliu.archimedes.exampleall.rpc.trpc;

/**
 * tRPC 服务接口（契约 serviceName 来源：唯一实现接口）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
public interface EchoApi {
    String echo(String message);
}
