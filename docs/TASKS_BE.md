# 后端任务审计与同步报告

基于提供的《技术设计文档》与当前任务池（为空），我已执行审计与同步逻辑。以下是生成的后端任务列表，严格遵循核心法则。

## 后端任务列表 (TASKS_BE.md)

- [review] Task-BE-001: [Base] 项目初始化 | 搭建 Spring Boot 3 骨架，集成 MyBatis-Plus、Security、全局异常处理器和统一响应包装 | Ref: 5.1, 6.1, 6.2 | Feedback: None
- [todo] Task-BE-002: [Auth] 实现短信验证码发送接口 | 创建 `/auth/sms/send` 接口，集成阿里云 SDK，在 Redis 中设置 5 分钟过期时间并实现发送频控 | Ref: 6.3, 8.2 | Feedback: None
- [todo] Task-BE-003: [Auth] 实现登录与单设备绑定接口 | 创建 `/auth/login` 接口，校验验证码，管理用户设备绑定，签发含 `uid`、`deviceId`、`tokenVersion` 的 JWT | Ref: 3.1, 6.3, 8.1 | Feedback: None
- [todo] Task-BE-004: [Auth] 实现全局 JWT 鉴权过滤器 | 创建 `OncePerRequestFilter`，校验 JWT 合法性、设备匹配性及 `tokenVersion` 一致性 | Ref: 8.1 | Feedback: None
- [todo] Task-BE-005: [Data] 创建核心数据表 | 执行 DDL 创建 `user`, `user_device`, `relation`, `bind_code`, `relation_member_state`, `telemetry_event`, `alert_event` 表 | Ref: 7 | Feedback: None
- [todo] Task-BE-006: [Data] 创建 MyBatis-Plus Entity 与 Mapper | 为 `user`, `user_device`, `relation`, `bind_code`, `relation_member_state`, `telemetry_event`, `alert_event` 表生成 Entity 类和 Mapper 接口 | Ref: 5.1 | Feedback: None
- [todo] Task-BE-007: [Relation] 实现绑定码生成接口 | 创建 `/relation/bind-code` 接口，生成 6 位唯一短码并存入数据库，设置 TTL | Ref: 3.2, 6.4 | Feedback: None
- [todo] Task-BE-008: [Relation] 实现使用绑定码建立关系接口 | 创建 `/relation/bind` 接口，校验绑定码有效性及双方关系状态，在事务内创建关系并标记绑定码已使用 | Ref: 3.2, 6.4, 9 | Feedback: None
- [todo] Task-BE-009: [Relation] 实现双方确认解绑流程接口 | 创建 `/relation/unbind/request` 和 `/relation/unbind/confirm` 接口，管理解绑请求的生命周期与确认逻辑 | Ref: 3.5, 6.4 | Feedback: None
- [todo] Task-BE-010: [Telemetry] 实现心跳与状态上报接口 | 创建 `/telemetry/heartbeat` 接口，接收定位、设备、权限状态，更新快照表并写入事件流水，检测状态变化 | Ref: 3.3, 6.5, 9 | Feedback: None
- [todo] Task-BE-011: [Telemetry] 实现监视方查询对方状态接口 | 创建 `/telemetry/partner/state` 接口，从 `relation_member_state` 表查询并返回伙伴的最新状态 | Ref: 6.5 | Feedback: None
- [todo] Task-BE-012: [Alert] 实现告警事件生成与推送任务投递服务 | 创建 `AlertService`，当检测到权限状态变化或失联时，生成 `alert_event` 记录并异步投递推送任务，实现去重逻辑 | Ref: 3.3, 3.4, 8.4, 9 | Feedback: None
- [todo] Task-BE-013: [Schedule] 实现失联判定定时任务 | 创建 `@Scheduled` 任务，每分钟扫描 `relation_member_state` 表，对超过 5 分钟无心跳的用户置为 LOST 状态并触发告警 | Ref: 3.4, 9 | Feedback: None
- [todo] Task-BE-014: [Security] 实现接口限流与防刷 | 为短信发送、心跳上报等接口集成 Redis 实现限流（固定窗口/令牌桶），防止滥用 | Ref: 8.2, 8.4 | Feedback: None
- [todo] Task-BE-015: [Relation] 实现强制解除关系接口（预留） | 创建 `/relation/unbind/force` 接口框架，预留二次验证和敏感数据清理逻辑 | Ref: 3.5, 6.4, 8.3 | Feedback: None

---
**审计说明**:
1.  **孤儿任务检测**: 当前任务池为空，无孤儿任务。
2.  **增量更新**: 以上 15 个任务均从 DESIGN 文档中提取，覆盖了核心模块（基础、认证、关系、遥测、告警、调度、安全）。
3.  **状态与反馈**: 所有任务初始状态为 `[todo]`，`Feedback` 字段为 `None`。
4.  **ID 规范**: 严格遵循 `Task-BE-XXX` 格式，从 001 开始顺序递增。