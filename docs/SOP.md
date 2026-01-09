# 📄 铭宇 AI 驱动开发全链路标准作业程序 (SOP v2.1)

## 一、 核心哲学：单一信源与设计先行

* **设计即真理 (Design as Truth)**：`docs/design/` 是项目的最高准则。文档不更新，代码不准写。
* **意图对齐**：通过 `Execution Plan`（执行计划）在编码前完成人机共识，减少重写。
* **质量左移**：坚持 TDD 模式，单元测试与业务逻辑由 Coder Agent 同步完成，集成测试由 Test Agent 独立验证。

---

## 二、 角色定义与工具链

| 角色 | 职能描述 | 核心工具 |
| --- | --- | --- |
| **PM / Architect** | 定义业务目标，审核技术方案 | 人类 + Architect Agent |
| **Developer** | 流程守门员，审核执行计划与代码合并 | 人类 (CLI 控制) |
| **Coder Agent** | 实现业务逻辑、编写单元测试 (Unit Test) | Qwen-Coder-Turbo |
| **Test Agent** | 独立编写集成测试 (Integration Tests) | 独立 LLM (如 DeepSeek/GPT-4o) |
| **Reviewer Agent** | 代码逻辑审计，对比 PRD 与代码 Diff | 独立 LLM |

---

## 三、 核心资产结构

1. **`docs/design/` (单一信源)**:
* `backend.md` / `frontend.md`: 包含全局架构、API 契约、Schema、安全约束。
* `## Implementation Notes`: **[强制]** 记录 AI 踩过的坑和手动干预的经验。


2. **`docs/TASKS_*.md`**:
* 任务必须原子化（< 3天工作量），必须包含 `Reference` 链接到设计章节。



---

## 四、 阶段性实施流程

### 第一阶段：意图定义 (The Intent Phase)

1. **需求输入**: PM 在 `PRD.md` 中定义业务目标。
2. **设计生成**: 运行 `python -m agents.architect_agent -gd -all` 生成/更新设计。
3. **人类审核 [关键]**: 开发者审核设计文档中的接口定义和逻辑，确保无架构硬伤。
4. **任务同步与审计**: 运行 `python -m agents.architect_agent -st -all`。
* 检查任务是否在设计中有据可查，处理 `Orphan`（孤儿任务）和 `Duplicate`（重复任务）。



### 第二阶段：执行循环 (The Execution Phase - TDD 模式)

1. **任务启动**: 开发者指定 Task ID（如 `Task-BE-001`）。
2. **上下文加载**: Coder Agent 深度读取 `DESIGN.md` 相关章节与 `Implementation Notes`。
3. **输出执行计划**: Agent 输出 `Execution Plan`（涉及文件、修改步骤）。
* **人类输入 "Go" 确认后方可编码。**


4. **编码与单测 (TDD)**:
* Agent 编写接口骨架 -> 编写单元测试 -> 实现业务逻辑。
* **本地验证**: 运行 `mvn test` (后端) 或 `vitest` (前端)，必须全部 Pass。


5. **异常干预**: 若 AI 无法自愈，人工介入修复，并同步更新设计文档的 `Implementation Notes`。

### 第三阶段：质量闭环 (The Quality Phase)

1. **集成验证**: 唤起 **Test Agent**。
* 它**不看代码**，只读 `DESIGN.md`。
* 编写模拟真实调用链路的集成测试。若失败，说明 Coder 偏离了设计。


2. **逻辑审计**: 触发 **Reviewer Agent**。
* 对比 `PRD`、`Design` 与代码 `Diff`。
* 寻找未处理的边界条件或与文档不符的逻辑。



---

## 五、 落地执行步骤 (Roadmap)

| 阶段 | 目标 | 动作项 |
| --- | --- | --- |
| **Step 1** | **文档规范化** | 在 `docs/design/` 下建立符合“铭宇模板”的初版文档。 |
| **Step 2** | **CLI 环境预设** | 将 `architect_agent.py` 与三份核心 Prompt 集成到开发环境。 |
| **Step 3** | **试点开发** | 选择 1 个非核心模块（如：意见反馈），完整走一遍 TDD 循环。 |
| **Step 4** | **建立 Test Agent** | 编写脚本，让独立模型根据 `backend.md` 生成 RestAssured 集成测试。 |
| **Step 5** | **全流程推行** | 严格执行“计划不通过不编码，测试不通过不合并”的纪律。 |

---

## 六、 关键成功因素

* **设计新鲜度**: 任何代码层面的手动重构必须反馈给 `Design Doc`，否则 AI 幻觉会迅速增加。
* **计划确认**: 强制人类审查 `Execution Plan`，这是防止 AI 乱改全局代码的最后一道防线。
* **闭环学习**: `Implementation Notes` 是团队的数字资产，记录了针对特定框架（如 UniApp 保活）的踩坑经验。

---
