## 基于 Agent 的开发工具包

这个仓库已经搭建好一套自动化的多智能体（Architect / Coder / Reviewer）流水线：架构 Agent 会根据 PRD 生成任务清单，编码 Agent 基于任务编写代码并跑测试，审核 Agent 进行语义 & 测试复核。整套逻辑全部集中在 `.agents/` 目录；在操作 Agents 时可以完全忽略 `src/` 里的内容。

### 仓库结构（与 Agent 相关）
- `.agents/architect_agent.py`：读取 `docs/PRD.md`，把拆解后的任务写入 `docs/tasks.json`。
- `.agents/coder_agent.py`：挑选下一个 `"todo"` 任务，生成/更新文件，运行对应测试，再把任务状态改成 `"review"`。
- `.agents/reviewer_agent.py`：重新执行语义检查和测试，通过则标记 `"done"`，不通过则附带反馈退回 `"todo"`。
- `.agents/orchestrator.py`：可选的调度器，循环运行 Reviewer → Coder，直到任务全部完成或达到最大循环次数。
- `.agents/prompts/*.md`：各 Agent 的提示词模板，若需调整策略可在此修改。
- `.agents/lib/llm.py`：`codex exec` CLI 的轻量封装，`CODEX_MODEL` 环境变量用于指定模型。
- `docs/PRD.md`：产品需求文档，Architect Agent 的输入。
- `docs/tasks.json`：共享任务看板，所有 Agent 都会读写。

### 环境准备
1. **Python 3.11+**，安装依赖：`pip install -r requirements.txt`（目前仅 `termcolor`）。
2. **Codex CLI** 已安装并在 `PATH` 中，确保 `codex exec -m gpt-5-codex -` 可用。  
   - 如需使用其他模型，设置 `CODEX_MODEL` 环境变量。
3. （推荐）在运行 Agents 前验证 CLI 通路：
   ```bash
   python test_codex.py
   ```
   该脚本会调用 `.agents/lib/llm.py`，分别发起普通文本请求与 JSON 模式请求，确认 CLI 工作正常。

### 准备任务清单
1. 根据最新需求更新 `docs/PRD.md`。
2. 运行 Architect 生成任务：
   ```bash
   python .agents/architect_agent.py
   ```
   - 提示词会强制要求指定技术栈并输出严格的 JSON 数组，保存到 `docs/tasks.json`。
   - 每个任务仅使用 `todo` / `review` / `done` 三种状态，Agents 会依赖这些状态，不要手动改动。

### 运行 Agents
可以交给 orchestrator 自动调度，也可以手动执行。

#### 方案 A：一键流水线
```bash
python .agents/orchestrator.py
```
- 会打印循环进度，优先处理 Reviewer，再调度 Coder。
- 当没有 `"todo"` / `"review"` 任务或已循环 50 次（避免 LLM 死循环）时自动退出。

#### 方案 B：手动控制
1. **Coder 阶段**
   ```bash
   python .agents/coder_agent.py
   ```
   - 从 `docs/tasks.json` 中读取首个 `"todo"` 任务以及可能的 `feedback`。
   - 将当前项目文件树提供给 LLM，落地生成的文件，并自动识别测试类型运行：
     - Java：`mvn -Dtest=<ClassName> test`
     - Python：`pytest <file>`
     - 前端：`npm run test`
   - 成功后回写 `file_path`，清空反馈，把任务标记为 `"review"`。

2. **Reviewer 阶段**
   ```bash
   python .agents/reviewer_agent.py
   ```
   - 读取首个 `"review"` 任务。
   - 启用 JSON 模式的审查提示词，检查重复/退化。
   - 使用与 Coder 相同的规则重新运行测试。
   - 失败则附带原因退回 `"todo"`；通过则设为 `"done"`。

重复 Coder → Reviewer 流程，直到该任务被接受为止。

### 自定义提示词与行为
- `.agents/prompts/` 中的 Markdown 模板已包含 `{{task_title}}`、`{{prd_content}}` 等占位符，编辑时请保留这些变量。
- 若仓库结构变更，可修改各 Agent 中的 `get_file_tree`，以调整生成文件树时需要忽略的目录。
- 如需支持更多测试框架，可扩展 `run_tests` / `run_tests_polyglot` 的逻辑。

### 使用 `docs/tasks.json`
- 将其视为唯一的协作渠道；Agents 运行时不要手动改状态。
- 任务的最小字段结构示例：
  ```json
  {
    "id": "BE-002",
    "type": "backend",
    "title": "AuthController Login Flow",
    "description": "...",
    "acceptance_criteria": "...",
    "priority": "P0",
    "prd_ref": "REQ-1",
    "status": "todo"
  }
  ```
- Coder 在完成任务后会添加 `file_path`，Reviewer 若发现问题则写入 `feedback`。若保留旧反馈，Coder 下次会继续处理。

### 常见问题
- **找不到任务**：检查 `docs/tasks.json` 是否存在且包含至少一个 `"todo"` 任务。
- **LLM 调用失败**：确认 `codex` CLI 已登录且模型可用，封装会在失败时打印 stderr。
- **测试命令缺失**：根据生成代码安装 Maven / Node / PyTest 等必要工具。
- **Orchestrator 卡住**：它会在 50 次循环后停止；查看 `docs/tasks.json` 是否在 `todo`/`review` 之间来回跳转，必要时调整提示词或手动修复仓库。

按照以上流程即可完全依赖 `.agents` 目录完成“规划 → 编码 → 评审”的闭环迭代，而无需直接修改 `src/` 下的具体实现。

## 本地 Docker 开发栈
<!-- // Implements System -->
1. 复制配置：`cp .env.example .env`，根据需要修改数据库密码、端口和 `APP_PROFILE`。
2. 授权脚本：`chmod +x scripts/start-stack.sh scripts/stop-stack.sh`（只需运行一次）。
3. 启动：`./scripts/start-stack.sh` 会在后台启动 MySQL 8.0 与 Redis，并在终端输出健康状态；再次运行会复用持久化卷。
4. 停止：`./scripts/stop-stack.sh` 会保留 `mysql_data` 与 `redis_data` 卷，确保数据在重启后依旧存在。
5. 查看状态：使用 `docker compose ps` 或 `docker compose logs <service>` 获取健康检查结果与日志。