# 关系状态感知 App 后端开发任务列表 (TASKS.md)

## 任务状态说明
- `[ ]` 待开发
- `[x]` 已完成
- `[-]` 进行中
- `[?]` 阻塞/待定

## 核心任务 (P0)

- [ ] Task-001: 项目初始化与基础框架搭建 | 创建 Spring Boot 3 项目，集成 MyBatis-Plus、Spring Security、Redis 等核心依赖，配置统一响应与异常处理。
  - Reference: 5.1 模块分层、8.1 鉴权与会话
  - Priority: P0

- [ ] Task-002: 数据库表结构创建 | 根据 DDL 创建所有核心表（user, user_device, relation, bind_code, relation_member_state, telemetry_event, alert_event）及预留表。
  - Reference: 7. 数据设计
  - Priority: P0

- [ ] Task-003: 短信验证码发送与校验服务 | 实现短信验证码的发送（Redis存储）、频控（60秒冷却）、校验逻辑。
  - Reference: 3.1 手机号登录、8.2 短信验证码安全
  - Priority: P0

- [ ] Task-004: 用户登录与单设备绑定 | 实现 `/auth/login` 接口，完成验证码校验、用户记录创建/更新、单设备绑定校验、JWT签发（含tokenVersion）。
  - Reference: 3.1 手机号登录、6.3 登录接口、8.1 鉴权与会话
  - Priority: P0

- [ ] Task-005: JWT请求过滤器与设备校验 | 实现 Spring Security Filter 或 Interceptor，校验 JWT 合法性、deviceId 及 tokenVersion 与数据库的一致性。
  - Reference: 8.1 鉴权与会话
  - Priority: P0

- [ ] Task-006: 绑定码生成与消费服务 | 实现 `/relation/bind-code` 和 `/relation/bind` 接口，包括6位码生成（唯一、短TTL）、1 vs 1关系约束校验、关系创建。
  - Reference: 3.2 绑定码建立关系、6.4 Relation接口
  - Priority: P0

- [ ] Task-007: 心跳与状态上报服务 | 实现 `/telemetry/heartbeat` 接口，完成数据写入 `relation_member_state` 快照表和 `telemetry_event` 事件表，并检测权限状态变化。
  - Reference: 3.3 心跳与状态上报、6.5 Telemetry接口
  - Priority: P0

- [ ] Task-008: 状态变化告警生成与推送任务投递 | 在心跳服务中，当检测到关键权限状态变化时，生成 `alert_event` 记录并异步投递推送任务。
  - Reference: 3.3 心跳与状态上报、4. 方案概览（60秒触达）
  - Priority: P0

- [ ] Task-009: 失联判定调度任务 | 实现 Spring `@Scheduled` 任务，每分钟扫描 `relation_member_state` 表，对超过5分钟未心跳的用户更新状态为 LOST 并生成告警。
  - Reference: 3.4 失联判定（5分钟）
  - Priority: P0

- [ ] Task-010: 监视方查询对方最新状态 | 实现 `/telemetry/partner/state` 接口，供监视方查询被监视方的最新快照信息。
  - Reference: 6.5 Telemetry接口
  - Priority: P0

- [ ] Task-011: 双方确认解绑流程 | 实现 `/relation/unbind/request` 和 `/relation/unbind/confirm` 接口，完成解绑请求创建、确认及关系终止。
  - Reference: 3.5 解绑（双方确认）
  - Priority: P0

## 增强任务 (P1)

- [ ] Task-012: 强制解除关系出口 | 实现 `/relation/unbind/force` 接口，包含二次验证（如短信）、数据清理（逻辑删除）逻辑。
  - Reference: 3.5 解绑与强制解除出口、8.3 敏感数据处理
  - Priority: P1

- [ ] Task-013: 高频写入优化与幂等性 | 为心跳上报接口实现基于 `userId + timestamp` 的幂等性控制（Redis SETNX），并优化快照表 upsert 性能。
  - Reference: 4. 方案概览、9. Implementation Notes (幂等性、高频写入优化)
  - Priority: P1

- [ ] Task-014: 告警推送去重与重试机制 | 完善 `AlertService`，实现基于 `dedup_key` 的告警去重（Redis），以及推送失败后的重试策略。
  - Reference: 8.4 防刷与滥用、9. Implementation Notes (60秒触达)
  - Priority: P1

- [ ] Task-015: 接口限流与防刷 | 为关键接口（如心跳上报、发送短信）实现基于用户或关系的限流（Redis Token Bucket）。
  - Reference: 8.4 防刷与滥用
  - Priority: P1

- [ ] Task-016: 数据模型预留字段与扩展点 | 创建 P1 预留的 `Subscription`, `AuditUnlockEvent`, `AuditLookusEvent` 等实体类、Mapper 及基础结构。
  - Reference: 5.1 模块分层、7. 数据设计（P1预留表）
  - Priority: P1

## 优化与测试任务 (P2)

- [ ] Task-017: 关键服务层单元测试 | 为 `AuthService`, `RelationService`, `TelemetryService` 编写单元测试，覆盖绑定码唯一性、设备绑定、状态变化检测等核心逻辑。
  - Reference: 9. Implementation Notes (测试规范)
  - Priority: P2

- [ ] Task-018: 集成测试与API文档 | 使用 SpringDoc OpenAPI 生成 Swagger 接口文档，并编写关键业务流程的集成测试用例。
  - Reference: 1. 文档信息（接口文档）
  - Priority: P2

- [ ] Task-019: 配置与部署准备 | 整理 `application.yml` 配置模板（数据库、Redis、JWT密钥等），编写基本的 Dockerfile 或部署说明。
  - Reference: 1. 文档信息（部署说明）
  - Priority: P2

- [ ] Task-020: 监控与日志增强 | 集成应用监控（如Spring Boot Actuator），确保敏感数据（如手机号、定位）在日志中脱敏。
  - Reference: 8.3 敏感数据处理
  - Priority: P2