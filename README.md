# 🚀 铭宇 AI 驱动开发协作平台

这是一个基于 **Agent-Centric（以智能体为中心）** 理念构建的自动化开发工作流。本项目利用多模型协作（DeepSeek, Qwen, GPT-4o），实现从 **PRD -> 架构设计 -> 任务拆解 -> 自动编码** 的全链路闭环。

---

## 🛠 技术栈

* **后端**: Java 17, Spring Boot 3, MyBatis-Plus, MySQL 8.0
* **前端**: UniApp (Vue 3 + Vite), Pinia
* **AI 核心**:
* `Architect Agent`: 负责架构设计与 API 契约定义。
* `Task Broker`: 负责任务审计、去重与同步。
* `Coder Agent`: 基于 TDD 模式的自动代码生成。



---

## 📂 核心资产与目录结构

* **`docs/PRD.md`**: 业务需求的原始信源。
* **`docs/design/`**: **[单一信源]** 存储 AI 生成并经人工审核的后端 (`backend.md`) 和前端 (`frontend.md`) 设计。
* **`docs/TASKS_*.md`**: 任务池，记录所有待执行的原子化开发任务。
* **`agents/`**: 存放 Agent 的逻辑实现、Prompt 模板及核心驱动。

---

## 🔄 标准作业程序 (SOP)

项目遵循“设计先行”原则，严禁跳过设计直接编码：

### 1. 意图定义 (Intent Phase)

将业务需求转化为技术语言：

```bash
# 生成/更新前后端设计文档
python -m agents.architect_agent -gd -all

```

*在此阶段，人类架构师需审核 `docs/design/` 下的文档，确保 API 契约对齐。*

### 2. 任务分发 (Task Phase)

将设计文档拆解为可执行的原子任务：

```bash
# 同步并审计任务列表
python -m agents.architect_agent -st -all

```

*Task Broker 会自动进行语义去重，并对无设计依据的任务标记 `Orphan`。*

### 3. 执行循环 (Execution Phase)

程序员选择 Task ID，激活 Coder Agent 进行 TDD 开发。
*(当前阶段正在完善 Coder Agent 的对接中...)*

---

## 🚀 快速开始

1. **环境配置**:
* 安装依赖：`pip install termcolor requests`（根据实际 `llm.py` 需求）。
* 在 `agents/config.py` 中配置你的 API Key 和模型路由。


2. **首次运行**:
* 准备好 `docs/PRD.md`。
* 运行设计生成：`python -m agents.architect_agent -gd -all`。
* 查看生成的 `docs/design/` 文件夹。



---

## ⚠️ 开发者注意事项

* **不要直接修改代码**: 所有的逻辑变更应先反映在 `Design Doc` 中，再由 Agent 同步。
* **Implementation Notes**: 在设计文档中记录的手动干预经验是 AI 减少幻觉的关键资产。
* **API 强一致性**: 前端设计必须严格遵守后端定义的字段名。

---

### 下一步计划

* [x] 完成 Architect Agent 命令行工具。
* [x] 实现后端设计与前端设计的 API 自动对齐逻辑。
* [ ] 激活 Coder Agent (Qwen) 的任务领用功能。
* [ ] 建立 Test Agent 自动化黑盒测试闭环。

---