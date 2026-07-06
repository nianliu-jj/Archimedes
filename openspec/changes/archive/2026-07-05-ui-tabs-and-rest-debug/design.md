# Design: ui-tabs-and-rest-debug

## Context

UI 是 core 资源目录下的单文件 `archimedes-ui/index.html`，由 `ArchimedesApiController#ui()` 读取、替换 `__ARCHIMEDES_API_URL__` 占位符后缓存返回。约束：零构建步骤、零外部 CDN 依赖（内网可用）、ES5 兼容语法（与现有代码一致）。本变更不触碰任何 Java 代码。

## Goals / Non-Goals

- Goals：Tab 化、筛选增强、REST try-it、RPC metadata 展示；保持单文件自包含。
- Non-Goals：不做前端框架化/构建链；不做 WebSocket/RPC 在线调试（协议复杂度不适合浏览器直连）；不做服务端代理转发（try-it 直接同源 fetch）。

## Decisions

### D1：Tab 集合 = REST / WebSocket / RPC / TR / Trace（5 个）
功能清单 12.1 原文列出五类。TR（SOFA_TR/TRPC）与 RPC（DUBBO/GRPC）共用 `rpcApis` 数据源，前端按 `protocol` 字段二分：`TR_PROTOCOLS = ['SOFA_TR','TRPC']`，其余（含未来新协议）归 RPC Tab。两 Tab 复用同一 `renderRpcInto()` 渲染函数，只是数据切片与协议 chip 集不同。
备选：4 Tab（TR 并入 RPC）——被否，需求语境里 TR 是与 RPC 并列的一等协议族。

### D2：Tab 切换纯 CSS class 显隐 + URL hash 记忆
`.panel { display:none } .panel.active { display:block }`；`switchTab(name)` 更新按钮/面板 active 状态并写 `location.hash`；加载时从 hash 恢复。不引入路由库。

### D3：筛选模型 = 全局文本 + Tab 局部维度
- 全局输入框保留，作用于当前激活 Tab（渲染函数全部接收 `q`）。
- REST：HTTP 方法 `<select>`（GET/POST/...，选项由数据动态聚合）。
- RPC/TR：协议 chip（点击切换，"ALL" 复位），chip 集由该 Tab 数据动态聚合。
- 计数格式沿用 `(命中/总数)`，同时映射到 Tab 标签的总数徽标。

### D4：try-it 为行内展开面板，一次只开一个
点击 REST 行的"调试"按钮，在该行下方插入 `<tr class="try">`（colspan 全宽）承载表单；再次点击或点击其他行时收起/切换。相比固定侧栏，行内展开保留契约上下文且无需额外布局层。

表单预填规则（来自契约 `params`）：
- `PATH` → 路径变量输入框（从 path 的 `{var}` 占位与 params 交叉）；
- `QUERY` → 查询参数行（name 预填、required 标星）；可手动增删行；
- `HEADER` → 请求头行；可手动增删行；
- `BODY` → 显示请求体 textarea，默认 `Content-Type: application/json`、预填 `{}`；无 BODY 参数且方法为 GET/HEAD 时隐藏 textarea。
发送：`fetch(url, {method, headers, body})`（GET/HEAD 不带 body），`performance.now()` 计耗时；响应体尝试 `JSON.parse` 成功则 2 空格缩进美化；响应头全量列出。

### D5：traceId 联动
响应头中查找名称含 `trace`（大小写不敏感，覆盖默认 `X-Trace-Id` 与用户自定义名）的首个头；命中则渲染"查看链路日志"按钮 → `switchTab('trace')` + 回填输入框 + 自动 `queryTrace()`。trace 头名可配置而 UI 无从得知配置值，模糊匹配是无后端改动下的务实解。

### D6：RPC metadata 展示
`RpcApiInfo.metadata`（Map，可空）在 Service 单元格下方以灰色小字 `k=v · k=v` 渲染；空 map 不渲染。不做展开交互，信息量小无需折叠。

### D7：e2e 断言锚点
双 starter `EndToEndTest` 的 UI 用例补一条：body 含 `id="tabs"`（Tab 导航容器，稳定锚点）。既有断言（含注入地址、无占位符残留）不动。

## Risks / Trade-offs

- 单文件体积增长（约 200→450 行）：可接受，零构建约束下的必然；函数按 Tab 分组组织。
- try-it 对非 JSON 响应（二进制等）按文本展示：可接受，调试场景以 JSON 为主。
- 模糊匹配 trace 头可能误中宿主自定义含 "trace" 的无关头：概率低，且仅影响联动按钮的跳转值，无破坏性。

## Migration Plan

整页替换，无数据迁移。宿主升级依赖版本后刷新页面即得新 UI。

## Open Questions

无。
