package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;

import java.util.List;

/**
 * RPC 契约贡献者 SPI。每个 RPC 协议（Dubbo/gRPC/SOFARPC-TR/tRPC）各一个实现，
 * 由 starter 按 classpath 条件装配，控制器聚合所有在场贡献者的结果。
 */
public interface RpcApiContributor {

    List<RpcApiInfo> contribute();
}
