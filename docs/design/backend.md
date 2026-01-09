## 1. **文档信息**
- **标题**：关系状态感知 App（v1.0）后端技术架构设计（Spring Boot 3 / MySQL / Redis）
- **状态**：评审中
- **相关链接**：PRD 链接（TBD）、原型（TBD）、接口文档（TBD / Swagger）、ER 图（TBD）、部署说明（TBD）

---

## 2. **目的与范围 [必填]**
**要解决的业务问题**
- 支撑 **1 vs 1 关系绑定**（绑定码建立关系、解绑流程与强制解除出口）。
- 支撑 **高频后台保活数据上报**：被监视方周期性上报定位/设备状态/权限存活，监视方实时查看。
- 支撑 **Anti-Escape 权限状态可见**：对方关闭定位/通知/卸载等状态变化需 **60 秒内**触达监视方（通过 Push/站内通知通道）。
- 支撑 **失联判定**：5 分钟未收到心跳，状态置为“未知/失联”并告警。
- 为 P1 商业化（轨迹回放、解锁审计、Lookus 记录、敏感动作日志）预留数据模型与扩展点。

**范围（v1.0）**
- 账号：手机号+验证码登录、单设备绑定、换机自动解绑。
- 关系：绑定码生成/使用、关系查询、解绑（双方确认）+ 单向强制解除（清空数据出口）。
- 上报：心跳/定位/设备状态/权限状态上报与聚合查询。
- 通知：状态变更触发推送任务入队（实际 Push 通道对接作为适配层）。

**Non-goals（明确不做）**
- 不实现前端/客户端权限弹窗与原生能力，只提供后端接口与状态存储。
- 不实现真实地图服务/逆地理编码（可由客户端或后续服务完成）。
- 不在 v1.0 内实现完整支付/订阅扣费闭环（仅预留会员与审计表结构）。
- 不提供多对多关系（严格 1 vs 1）。

---

## 3. **核心用例与流程 [必填]**
### 3.1 手机号登录（单设备绑定）
1. 客户端请求发送短信验证码 `/auth/sms/send`
2. 客户端提交验证码登录 `/auth/login`
3. 服务端：
   - 校验验证码（Redis）
   - 创建/更新用户记录
   - 校验设备绑定：若用户已绑定其他 device，则 **解绑旧 device 并使其 token 失效**
   - 签发 JWT（含 `uid`、`deviceId`、`tokenVersion`）
4. 客户端后续请求携带 JWT；服务端对 `deviceId` 与 `tokenVersion` 做一致性校验，防止旧设备继续访问

### 3.2 绑定码建立 1 vs 1 关系
- **A 生成绑定码**：A 调用 `/relation/bind-code` 获取 6 位码（短 TTL + 唯一）
- **B 输入绑定码建立关系**：B 调用 `/relation/bind` 提交 code
- 服务端校验：
  - code 有效且未过期、未使用
  - A 与 B 均当前未处于其他有效关系（1 vs 1 约束）
  - 创建 relation（双向可查询，但角色字段区分“监视方/被监视方”由业务定义或由客户端配置）
  - 标记 code 已使用
- 返回 relationId 与双方基础信息

### 3.3 心跳与状态上报（1 分钟/地理围栏触发）
- 被监视方周期性调用 `/telemetry/heartbeat`（包含定位/电量/网络/权限状态摘要）
- 服务端写入：
  - `relation_member_state` 最新状态（覆盖式 upsert）
  - `telemetry_event` 事件流水（可按开关配置是否落全量）
- 若检测到权限状态关键字段变化（如 locationEnabled 从 1->0），写入 `alert_event` 并触发推送任务（异步）

### 3.4 失联判定（5 分钟）
- 调度任务（Spring @Scheduled 或 MQ/延迟队列）每分钟扫描：
  - 若 `now - lastHeartbeatAt >= 5min` 且当前状态非失联，则置为 LOST 并生成告警
- 告警通过 pushAdapter 发给监视方（目标：60 秒内触达）

### 3.5 解绑（双方确认）与强制解除出口
- 双方确认解绑：A 发起 `/relation/unbind/request`，B 确认 `/relation/unbind/confirm`
- 强制解除（预留）：`/relation/unbind/force`（需要更高鉴权：如二次验证码/风控/人工审核开关），执行：
  - 关系置为终止
  - 清空/逻辑删除与该 relation 关联的敏感数据（轨迹、审计、事件）

---

## 4. **方案概览 [必填]**
**TL;DR 核心设计选择**
- **关系强约束**：数据库层面用唯一约束保证用户同一时间只属于一个有效关系（避免并发绑多次）。
- **状态与事件分离**：`relation_member_state` 存“最新快照”；`telemetry_event/alert_event` 存“可审计流水”，兼顾实时与审计。
- **60 秒触达**：状态变更 → 写告警事件 → Redis 去重/限流 → 异步推送任务（可重试），避免接口阻塞。
- **单设备绑定安全**：JWT + `tokenVersion` + device 绑定校验，旧设备自动失效。
- **高频写入优化**：快照表用 upsert；事件表按需落库并做分区/索引；Redis 辅助热点查询与幂等去重。

---

## 5. **架构与组件拆分**
### 5.1 模块分层（Spring Boot 3 + MyBatis-Plus）
- **Controller**
  - `AuthController`：短信发送、登录、登出
  - `RelationController`：绑定码、绑定、解绑、关系查询
  - `TelemetryController`：心跳/定位/状态上报、监视方查询对方状态
  - `AuditController`（P1 预留）：Lookus 记录、解锁/使用时长上传
- **Service**
  - `AuthService`：验证码校验、JWT 签发、设备绑定管理
  - `RelationService`：绑定码生成与消费、关系一致性校验、解绑流程
  - `TelemetryService`：快照更新、事件入库、状态变化检测、告警生成
  - `AlertService`：告警去重、推送任务投递、重试策略
- **Mapper（MyBatis-Plus）**
  - `UserMapper`, `UserDeviceMapper`
  - `RelationMapper`, `BindCodeMapper`
  - `RelationMemberStateMapper`, `TelemetryEventMapper`, `AlertEventMapper`
  - `SubscriptionMapper`（预留）
- **Entity**
  - `User`, `UserDevice`
  - `Relation`, `BindCode`
  - `RelationMemberState`, `TelemetryEvent`, `AlertEvent`
  - `Subscription`, `AuditUnlockEvent`, `AuditLookusEvent`（预留）

---

## 6. **接口设计 [必填]**
### 6.1 通用约定
- Base URL：`/api/v1`
- 鉴权：除登录/发码外均需 `Authorization: Bearer <JWT>`
- 响应包装：
```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

### 6.2 错误码定义（示例，需全局枚举）
| code | 含义 | 备注 |
| --- | --- | --- |
| 0 | 成功 | - |
| 40001 | 参数错误 | 校验失败 |
| 40100 | 未登录/Token 无效 | JWT 过期/签名错误 |
| 40101 | 设备不匹配 | 单设备绑定校验失败 |
| 40300 | 无权限 | 非关系成员/越权 |
| 40901 | 关系冲突 | 已存在有效关系 |
| 40902 | 绑定码无效 | 过期/已使用/不存在 |
| 42900 | 请求过频 | 上报限流 |
| 50000 | 系统异常 | - |

### 6.3 Auth
#### 发送短信验证码
- URL：`/api/v1/auth/sms/send`
- Method：`POST`
- Request
```json
{
  "phone": "13800000000",
  "scene": "LOGIN"
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "cooldownSeconds": 60
  }
}
```

#### 登录（单设备绑定）
- URL：`/api/v1/auth/login`
- Method：`POST`
- Request
```json
{
  "phone": "13800000000",
  "smsCode": "123456",
  "deviceId": "A1B2C3",
  "deviceModel": "iPhone15,3",
  "platform": "IOS"
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "token": "jwt.token.here",
    "expiresInSeconds": 604800,
    "user": {
      "id": 1001,
      "phoneMasked": "138****0000"
    }
  }
}
```

### 6.4 Relation
#### 生成绑定码
- URL：`/api/v1/relation/bind-code`
- Method：`POST`
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "bindCode": "483920",
    "ttlSeconds": 300
  }
}
```

#### 使用绑定码建立关系
- URL：`/api/v1/relation/bind`
- Method：`POST`
- Request
```json
{
  "bindCode": "483920"
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "relationId": 9001,
    "partnerUserId": 1002,
    "status": "ACTIVE"
  }
}
```

#### 发起解绑（双方确认）
- URL：`/api/v1/relation/unbind/request`
- Method：`POST`
- Request
```json
{
  "reason": "USER_REQUEST"
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "unbindRequestId": 7001,
    "expiresInSeconds": 86400
  }
}
```

#### 确认解绑
- URL：`/api/v1/relation/unbind/confirm`
- Method：`POST`
- Request
```json
{
  "unbindRequestId": 7001,
  "confirm": true
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "relationId": 9001,
    "status": "TERMINATED"
  }
}
```

#### 强制解除（预留出口）
- URL：`/api/v1/relation/unbind/force`
- Method：`POST`
- Request
```json
{
  "secondFactor": {
    "type": "SMS",
    "code": "123456"
  },
  "purgeData": true
}
```

### 6.5 Telemetry（心跳/状态）
#### 心跳上报（含定位与权限摘要）
- URL：`/api/v1/telemetry/heartbeat`
- Method：`POST`
- Request
```json
{
  "timestamp": 1700000000000,
  "location": {
    "lat": 31.2304,
    "lng": 121.4737,
    "accuracyMeters": 25
  },
  "device": {
    "batteryPercent": 78,
    "networkType": "WIFI",
    "wifiSsid": "MyWiFi"
  },
  "permission": {
    "locationAlways": true,
    "notificationEnabled": true,
    "batteryWhitelist": true,
    "usageAccess": false,
    "appInstalled": true
  }
}
```
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "serverTime": 1700000000123
  }
}
```

#### 监视方查询对方最新状态
- URL：`/api/v1/telemetry/partner/state`
- Method：`GET`
- Response
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "partnerUserId": 1002,
    "lastHeartbeatAt": 1700000000000,
    "onlineStatus": "ONLINE",
    "location": {
      "lat": 31.2304,
      "lng": 121.4737,
      "updatedAt": 1700000000000
    },
    "device": {
      "batteryPercent": 78,
      "networkType": "WIFI"
    },
    "permission": {
      "locationAlways": true,
      "notificationEnabled": true,
      "appInstalled": true
    }
  }
}
```

---

## 7. **数据设计 [必填]**
> 说明：以下 DDL 为 v1.0 核心 + P1 预留字段；可按实际落地拆分与分区策略。

```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(20) NOT NULL,
  `phone_hash` CHAR(64) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  UNIQUE KEY `uk_user_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_device` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `device_id` VARCHAR(128) NOT NULL,
  `platform` VARCHAR(16) NOT NULL,
  `device_model` VARCHAR(64) DEFAULT NULL,
  `token_version` INT NOT NULL DEFAULT 1,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `last_login_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device_user` (`user_id`),
  UNIQUE KEY `uk_user_device_device` (`device_id`),
  KEY `idx_user_device_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_a_id` BIGINT NOT NULL,
  `user_b_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL, -- ACTIVE / TERMINATED
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `terminated_at` DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation_user_a` (`user_a_id`, `status`),
  UNIQUE KEY `uk_relation_user_b` (`user_b_id`, `status`),
  KEY `idx_relation_pair` (`user_a_id`, `user_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `bind_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` CHAR(6) NOT NULL,
  `owner_user_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL, -- ACTIVE / USED / EXPIRED
  `expires_at` DATETIME(3) NOT NULL,
  `used_by_user_id` BIGINT DEFAULT NULL,
  `used_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bind_code_code` (`code`),
  KEY `idx_bind_code_owner` (`owner_user_id`),
  KEY `idx_bind_code_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `relation_member_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `relation_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `online_status` VARCHAR(16) NOT NULL, -- ONLINE / LOST / UNKNOWN
  `last_heartbeat_at` DATETIME(3) NOT NULL,
  `lat` DECIMAL(10,7) DEFAULT NULL,
  `lng` DECIMAL(10,7) DEFAULT NULL,
  `location_accuracy_m` INT DEFAULT NULL,
  `battery_percent` INT DEFAULT NULL,
  `network_type` VARCHAR(16) DEFAULT NULL,
  `wifi_ssid` VARCHAR(64) DEFAULT NULL,
  `permission_location_always` TINYINT NOT NULL DEFAULT 0,
  `permission_notification_enabled` TINYINT NOT NULL DEFAULT 0,
  `permission_battery_whitelist` TINYINT NOT NULL DEFAULT 0,
  `permission_usage_access` TINYINT NOT NULL DEFAULT 0,
  `app_installed` TINYINT NOT NULL DEFAULT 1,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation_member` (`relation_id`, `user_id`),
  KEY `idx_relation_member_relation` (`relation_id`),
  KEY `idx_relation_member_heartbeat` (`last_heartbeat_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `telemetry_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `relation_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `event_type` VARCHAR(32) NOT NULL, -- HEARTBEAT / LOCATION / DEVICE / PERMISSION
  `event_time` DATETIME(3) NOT NULL,
  `payload_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_telemetry_event_relation_time` (`relation_id`, `event_time`),
  KEY `idx_telemetry_event_user_time` (`user_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `alert_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `relation_id` BIGINT NOT NULL,
  `target_user_id` BIGINT NOT NULL, -- 监视方接收者
  `source_user_id` BIGINT NOT NULL, -- 被监视方触发者
  `alert_type` VARCHAR(32) NOT NULL, -- PERMISSION_CHANGED / LOST_HEARTBEAT / APP_UNINSTALLED
  `dedup_key` VARCHAR(128) NOT NULL,
  `status` VARCHAR(16) NOT NULL, -- NEW / SENT / FAILED
  `payload_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_dedup` (`dedup_key`),
  KEY `idx_alert_target_status` (`target_user_id`, `status`),
  KEY `idx_alert_relation_time` (`relation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- P1 预留：订阅（商业化）
CREATE TABLE `subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `relation_id` BIGINT NOT NULL,
  `plan` VARCHAR(16) NOT NULL, -- MONTH / QUARTER / YEAR
  `status` VARCHAR(16) NOT NULL, -- ACTIVE / EXPIRED
  `start_at` DATETIME(3) NOT NULL,
  `end_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_sub_relation` (`relation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 8. **安全性设计**
### 8.1 鉴权与会话（Spring Security + JWT）
- 使用 Spring Security 资源服务器模式或自定义 `OncePerRequestFilter` 校验 JWT。
- JWT Claims：`uid`、`deviceId`、`tokenVersion`、`exp`。
- 每次请求校验：
  1) JWT 合法  
  2) `user_device` 中 `user_id` 的 `device_id` 必须匹配  
  3) `tokenVersion` 必须等于 DB 的 `token_version`（换机/登出时自增，旧 token 全失效）

### 8.2 短信验证码安全
- 验证码存 Redis，包含 `phone+scene` 维度，TTL 5 分钟。
- 发送频控：同手机号 60 秒冷却、同 IP/设备额外限流（Redis 计数）。
- 验证次数限制：5 次失败锁定一段时间。

### 8.3 敏感数据处理
- 手机号：库内可存明文用于登录，但同时存 `phone_hash` 用于内部索引/脱敏展示；日志中只输出脱敏。
- 定位/轨迹：属于高敏数据
  - 接口与日志禁止打印 payload 明文
  - 数据导出/清理需要审计（P1 扩展）
- 强制解绑出口：需要二次因子（短信/人机校验）+ 运营开关（配置中心/DB 开关）

### 8.4 防刷与滥用
- 心跳上报接口限流：按 `userId` 或 `relationId` 每分钟次数限制（Redis token bucket / fixed window）。
- 告警推送去重：`dedup_key = relationId + sourceUserId + alertType + newValue`，防止频繁抖动刷屏。

---

## 9. **Implementation Notes**
- **并发与一致性（绑定码/关系）**：建立关系必须在事务内完成，依赖数据库 `UNIQUE` 约束兜底；出现 `DuplicateKey` 时转为业务码 `40901/40902`。
- **幂等性（心跳上报）**：客户端每次上报带 `timestamp`；服务端以 `userId + timestamp` 做幂等键（可选：Redis SETNX 60s），避免弱网重传导致事件重复。
- **60 秒触达**：接口内只做“写告警事件 + 投递推送任务”，推送发送异步化；推送失败要可重试并最终落 `FAILED`。
- **失联判定准确性**：以 `relation_member_state.last_heartbeat_at` 为准；调度扫描时注意分批分页与索引命中，避免全表扫。
- **高频写入优化**：快照表使用 MyBatis-Plus `saveOrUpdate`/自定义 upsert SQL；事件表可按天分区（后续扩展），避免长期膨胀影响查询。
- **数据清理（强制解除）**：清理逻辑要可配置（逻辑删除优先），并记录操作审计（谁、何时、为何）。
- **测试规范**：Service 层用 JUnit 5 + Mockito，重点覆盖：绑定码唯一性、单设备 tokenVersion 失效、权限状态变化触发告警、失联判定。

如你希望我把该设计进一步落到“可直接开工”的粒度（包结构、DTO/VO、表字段枚举、MyBatis-Plus 代码骨架、关键单测样例），告诉我你们的项目包名与是否已有统一返回体/异常处理规范。