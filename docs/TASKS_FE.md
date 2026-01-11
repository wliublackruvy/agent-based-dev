好的，项目管理与任务审计专家已就位。我将对您提供的《前端架构设计文档》进行审计，并与现有任务池进行比对、去重和同步，生成最新的前端任务列表。

---

### **审计报告与任务同步摘要**

1.  **设计文档分析**：文档已覆盖从项目初始化、登录、绑定、看板、地图到权限引导、弱网降级等完整核心链路，并明确了架构、状态管理和第三方插件选型。
2.  **孤儿任务检测**：`CURRENT_TASKS_POOL` 为空，无历史任务需要保留或标记为孤儿。
3.  **增量任务提取**：根据设计文档，我将提取并生成所有前端开发任务。任务ID将严格按照 `Task-FE-XXX` 格式，从001开始递增。
4.  **状态与反馈**：所有新任务初始状态均为 `[todo]`，`Feedback` 字段为 `None`。

---

### **FRONTEND TASKS LIST (v1.0)**

- [todo] Task-FE-001: [Base] 项目初始化与基础架构搭建 | 创建 UniApp Vue3 + Vite + Pinia 项目骨架；配置 pages.json 基础路由；封装统一的网络请求层（拦截器、错误码映射、超时重试逻辑）；定义 Pinia Store 类型结构 | Ref: 5.1, 5.2, 5.3 | Feedback: None
- [todo] Task-FE-002: [Auth] 实现登录页面与逻辑 | 开发登录页 UI；集成短信验证码发送 (`/auth/sms/send`) 与登录 (`/auth/login`) 接口；实现 Token 本地持久化与自动续期逻辑；处理登录后跳转（根据关系状态判断） | Ref: 3.1(2), 3.2 | Feedback: None
- [todo] Task-FE-003: [Relation] 实现关系绑定页面与逻辑 | 开发绑定页 UI；集成生成绑定码 (`/relation/bind-code`) 与绑定 (`/relation/bind`) 接口；处理绑定成功后的状态同步与页面跳转 | Ref: 3.1(3) | Feedback: None
- [todo] Task-FE-004: [Dashboard] 实现存活看板主页面 | 开发看板页 UI，包含状态卡片 (`StatusCard`)、心跳角标 (`HeartbeatBadge`) 等组件；集成轮询或页面可见时刷新伙伴状态 (`/telemetry/partner/state`) 的逻辑；展示在线状态、电量、网络、位置、权限摘要等信息 | Ref: 3.1(6), 4.2 | Feedback: None
- [todo] Task-FE-005: [Map] 实现地图页面 | 开发地图页 UI，集成 `map` 组件；实现地图模式切换 (`MapModeToggle`)；展示伙伴位置与距离；处理地图刷新节流 | Ref: 3.1(6), 4.2 | Feedback: None
- [todo] Task-FE-006: [Permission] 实现权限剧场化引导组件与逻辑 | 开发 `PermissionStepModal` 组件；实现分步引导流程（定位、通知、电池优化、使用情况访问）；集成系统设置跳转能力；记录引导完成状态 | Ref: 3.1(4), 7.1, 7.2 | Feedback: None
- [todo] Task-FE-007: [Telemetry] 实现被监视方心跳上报逻辑 | 封装心跳上报接口 (`/telemetry/heartbeat`)；实现前台定时上报（每1分钟）；集成弱网重传与本地缓存机制；组装包含定位、设备、权限摘要的上报 payload | Ref: 3.1(5), 3.2 | Feedback: None
- [todo] Task-FE-008: [Relation] 实现解绑流程页面与逻辑 | 在设置页开发解绑入口；集成发起解绑请求 (`/relation/unbind/request`) 与确认解绑 (`/relation/unbind/confirm`) 接口；实现强制解绑入口占位 UI | Ref: 3.1(7) | Feedback: None
- [todo] Task-FE-009: [Utils] 实现弱网与权限降级 UI 逻辑 | 在网络请求失败、心跳上报失败、状态查询失败时，实现统一的降级提示 UI；在权限被拒绝时，在看板展示对应提示并提供跳转设置按钮 | Ref: 3.2, 3.3 | Feedback: None
- [todo] Task-FE-010: [Plugin] 集成第三方插件与原生能力 | 评估并集成后台定位/地理围栏插件；评估并集成推送通知插件；封装设备信息与高级权限（电池白名单等）探测能力 | Ref: 6.1, 6.2, 6.3 | Feedback: None
- [todo] Task-FE-011: [App] 实现应用启动与全局逻辑 | 实现启动页逻辑（读取Token，判断登录与关系状态）；实现全局路由守卫与Token有效性检查；集成系统返回键/物理键监听（可选） | Ref: 3.1(1) | Feedback: None
- [todo] Task-FE-012: [Help] 实现帮助与排障页面 | 开发保活与权限排障手册页面 (`pages/help/keepalive`)；开发敏感动作记录/告警列表页面 (`pages/logs/index`) 的占位UI | Ref: 4.1 | Feedback: None

---
**审计完成**。以上任务列表已与设计文档严格对齐，并遵循了ID唯一性、状态保留等核心法则。所有任务均可作为开发依据。