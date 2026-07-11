# REST「试一试」面板左右两栏化 设计文档

- 日期：2026-07-11
- 作者：nianliu-jj
- 改动文件：`archimedes-core/src/main/resources/archimedes-ui/index.html`（唯一）

## 背景与目标

当前 REST 标签页的「试一试」面板（`try-cell`，由 `tryFormHtml` 渲染）把请求输入、字段说明、声明响应、实时结果全部**纵向堆叠**。信息密度高时需要大量上下滚动，请求与响应难以对照。

目标：把面板改为**左右两栏结构**——左侧集中「请求信息」，右侧集中「响应信息」，并在右栏顶部提供一个**常驻的「查看链路日志」跳转按钮**，一键跳到「链路追踪」标签页。

## 布局

用 flex 容器 `.try-split` 包裹，拆成左右两栏。

### 左栏 `.try-req`（请求）
1. 说明行（`api.summary` / `operationDescription`）
2. verb + path + **Send** 按钮 + 路径变量占位（`#tryPathVars`）
3. Query Params（含「+ param」）
4. Headers（含「+ header」）
5. Body（Content-Type + `#tryBody` textarea，按方法判断是否显示）
6. Request Fields 字段表（`schemaTableHtml('Request Fields', api.requestBodySchema)`）

### 右栏 `.try-resp`（响应）
1. **🔗 查看链路日志** 按钮（常驻，栏顶）
2. `#tryResult`（发送前为空占位；发送后显示 HTTP 状态 / 耗时 ms / Response Headers / Response Body）
3. Response Fields 字段表（`schemaTableHtml('Response Fields', api.responseSchema)`）
4. 声明的 Responses（`responsesHtml(api)`）

## 链路跳转按钮行为

- 常驻右栏顶部，读取全局变量 `tryLastTraceId`（在 `toggleTry` 切换接口时重置为 `null`）。
- **未取得 traceId**：仅 `switchTab('trace')` 打开「链路追踪」标签页，不自动查询。
- **发送后拿到 traceId**：`renderTryResult` 从响应头解析并写入 `tryLastTraceId`；点击按钮走 `jumpToTrace(tryLastTraceId)`（沿用原「切标签 + 填入 + 自动查询」逻辑）。
- 删除原先塞在 `#tryResult` 内、仅发送后出现的重复「View trace logs」按钮，统一到该常驻按钮。

## 响应式

- 两栏 `flex: 1 1 0; min-width: 320px`；容器 `display:flex; gap:16px; flex-wrap:wrap`。
- 视口较窄（约 860px 以下）时因 `flex-wrap` + `min-width` 自动纵向堆叠，接近原有观感，作为兜底。

## 非破坏性约束

- 不改后端、不改其它标签页。
- 保留 DOM id：`tryMethod`、`tryPath`、`tryPathVars`、`tryQuery`、`tryHeaders`、`tryCt`、`tryBody`、`tryResult`。
- `sendTryRequest` / `validateBeforeSend` / `jumpToTrace` / `renderTryPathVars` 逻辑不变；主要是新增 CSS + 调整 `tryFormHtml` 的 HTML 拼装分栏，及 `renderTryResult` 去掉内嵌 trace 按钮、改为写 `tryLastTraceId`。

## 验证

- 打开 UI，展开某个 REST 接口的「试一试」：确认左右两栏、字段归位正确。
- 未发送时点右栏「查看链路日志」→ 跳到链路追踪标签页。
- 发送一次带 traceId 的请求 → 右栏出现结果，点按钮 → 自动按该 traceId 查询链路。
- 缩窄窗口 → 两栏纵向堆叠不错位。
