# Agent-Based Development Workflow

本项目采用多 Agent 协作的开发流程，通过角色分工实现从设计到代码落地的自动化闭环。

## 核心 Agent 角色

| 角色 | 脚本 | 职责 |
| :--- | :--- | :--- |
| **Architect Agent** | `agents/architect_agent.py` | **设计师**。根据 PRD (`docs/PRD.md`) 生成或增量更新技术设计文档 (`docs/design/*.md`)。 |
| **Task Broker Agent** | `agents/orchestrator.py` | **项目经理**。将设计文档拆解为细粒度、高执行力的任务列表 (`docs/TASKS_*.md`)，确保任务原子性和输入输出明确。 |
| **Coder Agent** | `agents/coder_agent.py` | **程序员**。领取 `[todo]` 任务，编写业务代码和测试代码，并自动运行测试。若有 Review 反馈，则进行修复。 |
| **Reviewer Agent** | `agents/reviewer_agent.py` | **代码评审员**。审查 Coder 提交的代码，检查语义、安全、规范及测试覆盖率。通过则标记 `[done]`，否则驳回至 `[todo]` 并附带 Feedback。 |

## 工作流 (Workflow)

整个流程由 `Orchestrator` 和手动触发的脚本驱动，形成以下闭环：

```mermaid
graph TD
    A[PRD (docs/PRD.md)] -->|Architect| B(Design Docs)
    B -->|Task Broker| C(Task List)
    C -->|Coder| D{Code & Test}
    D -->|Coder (Run Tests)| E[Tests Passed?]
    E -- No --> D
    E -- Yes --> F[Reviewer Agent]
    F -->|Review| G{Pass Review?}
    G -- No (Feedback) --> C
    G -- Yes --> H[Task Done]
```

### 详细步骤

1.  **设计阶段 (Design Phase)**
    *   修改 `docs/PRD.md` 更新需求。
    *   运行 `python agents/orchestrator.py` (配置中开启 `GENERATE_DESIGN`)。
    *   **Architect** 会根据 PRD 更新 `docs/design/backend.md` 或 `frontend.md`。

2.  **任务分发阶段 (Task Sync)**
    *   运行 `python agents/orchestrator.py` (配置中开启 `SYNC_TASKS`)。
    *   **Task Broker** 读取最新的 Design，对比现有的 `TASKS_*.md`。
    *   执行**审计与同步**：
        *   **细粒度拆分**：将功能拆解为 DTO、Service、Controller 等原子任务。
        *   **明确 I/O**：每个任务包含详细的输入、输出描述。
        *   **状态保留**：保留已完成任务状态，标记已删除功能为孤儿任务。

3.  **开发阶段 (Coding Phase)**
    *   开发者或自动化脚本调用 **Coder Agent**：
        ```bash
        python agents/coder_agent.py --task Task-BE-002 --role be --yes
        ```
    *   **Coder** 读取任务详情、设计文档和现有代码上下文。
    *   生成/修改代码，并自动运行单元/集成测试。
    *   如果任务包含 `Feedback` (来自 Reviewer 的拒绝)，Coder 会读取当前代码快照进行**增量修复**而非重写。
    *   测试通过后，任务状态更新为 `[review]`。

4.  **评审阶段 (Review Phase)**
    *   调用 **Reviewer Agent**：
        ```bash
        python agents/reviewer_agent.py --role be
        ```
    *   **Reviewer** 扫描所有 `[review]` 状态的任务。
    *   进行**双重检查**：
        *   **语义审查** (LLM)：检查重复造轮子、安全性、命名规范、性能陷阱等。
        *   **自动化测试**：再次运行项目测试套件 (`mvn verify` / `npm test`) 确保无回归。
    *   **结果处理**：
        *   **通过**：更新任务为 `[done]`，写入 Feedback "Passed"。
        *   **拒绝**：更新任务为 `[todo]`，将具体修改建议写入 `Feedback` 字段。-> **回到开发阶段**。

## 目录结构规范

*   `agents/`: 存放所有 Agent 脚本及 Prompt。
*   `docs/`:
    *   `PRD.md`: 产品需求文档（单一信源）。
    *   `design/`: 技术设计文档。
    *   `TASKS_BE.md` / `TASKS_FE.md`: 任务追踪列表（Markdown 格式）。
*   `src/`: 源代码。

## 快速开始

1.  **环境准备**: 确保 Python 3.9+, Java 17+, Maven, Node.js 已安装。
2.  **初始化**:
    ```bash
    pip install -r requirements.txt
    ```
3.  **运行**:
    *   生成设计与任务: `python agents/orchestrator.py`
    *   开发后端任务 002: `python agents/coder_agent.py --task Task-BE-002 --role be --yes`
    *   评审后端: `python agents/reviewer_agent.py --role be`
