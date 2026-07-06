package io.github.nianliu.archimedes.scanner.rpc;

import io.github.nianliu.archimedes.model.RpcApiInfo;

import java.util.List;

/**
 * RPC 契约贡献者 SPI。每个 RPC 协议（Dubbo/gRPC/SOFARPC-TR/tRPC）各一个实现，
 * 由 starter 按 classpath 条件装配，控制器聚合所有在场贡献者的结果。
 *
 * @author nianliu-jj
 * @since 2026-07-06
 */
public interface RpcApiContributor {

    /** 贡献当前协议下宿主暴露的全部 RPC 服务契约。 */
    List<RpcApiInfo> contribute();
}
