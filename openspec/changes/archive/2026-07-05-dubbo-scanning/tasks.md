# Tasks: dubbo-scanning

## 1. core 通用 RPC 层

- [x] 1.1 `model/RpcApiInfo` + `model/RpcMethodInfo`；`scanner/rpc/RpcApiContributor` SPI
- [x] 1.2 `ApiCatalog` 增加 rpcApis（三参构造 + 二参委托）；控制器聚合 RpcApiContributor
- [x] 1.3 `scanner/rpc/DubboRpcScanner`（ServiceBean 扫描，接口方法反射提取，排除 Object 方法）
- [x] 1.4 core pom：dubbo optional（dubbo.version=3.2.16 由父 POM 属性管理）
- [x] 1.5 core 单测：DubboRpcScanner（构造 ServiceBean stub 或最小容器）、ApiCatalog 兼容

## 2. starter 与 UI

- [x] 2.1 双 starter 自动装配嵌套 DubboScanConfiguration（ConditionalOnClass ServiceBean）
- [x] 2.2 UI 新增 RPC APIs 分区（protocol 徽标 + 方法签名列表 + 空态）
- [x] 2.3 既有 EndToEnd 断言补 rpcApis 空数组

## 3. 集成测试与收尾

- [x] 3.1 双端 Dubbo 集成测试（dubbo-spring-boot-starter test 依赖；registry N/A、qos 关闭、随机协议端口；@DubboService 样例断言服务与方法签名）
- [x] 3.2 全量构建全绿
- [x] 3.3 README RPC 章节 + 功能清单勾选 Slice 8 + spec 同步 + 归档 + 提交
