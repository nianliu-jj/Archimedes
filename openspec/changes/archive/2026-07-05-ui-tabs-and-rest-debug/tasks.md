# Tasks: ui-tabs-and-rest-debug

- [x] 1. Tab 骨架：5 Tab 导航（REST/WebSocket/RPC/TR/链路追踪）+ CSS 显隐切换 + URL hash 记忆 + Tab 计数徽标；TR/RPC 按 protocol 二分复用同一渲染函数
- [x] 2. 筛选增强：全局文本过滤作用于当前 Tab；REST Tab 方法下拉；RPC/TR Tab 协议 chip；命中/总数计数
- [x] 3. REST try-it：行内展开面板（方法/路径变量/Query/Header/Body 预填）→ fetch → 状态/耗时/响应头/响应体（JSON 美化）
- [x] 4. traceId 联动：响应头模糊匹配 trace 头 → 一键切换链路 Tab 并自动查询
- [x] 5. RPC/TR metadata 键值详情渲染（空 map 不渲染）
- [x] 6. e2e 断言：双 starter EndToEndTest UI 用例补 `id="tabs"` 锚点断言；`mvn clean install` 全绿（124 测试）
- [x] 7. 真机验证：example jar 启动，curl 验证 UI 含 Tab 结构与 try-it 面板、无占位符残留、/apis 正常；模拟调试→trace 联动闭环（/api/trace/async → X-Trace-Id → 日志查询返回请求线程+异步线程 2 条）
