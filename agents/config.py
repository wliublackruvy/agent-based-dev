import os

# --- 路径配置 ---
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = os.path.join(BASE_DIR, "docs")

PRD_FILE = os.path.join(DOCS_DIR, "PRD.md")
DESIGN_FILE = os.path.join(DOCS_DIR, "DESIGN.md")
TASKS_FILE = os.path.join(DOCS_DIR, "TASKS.md")
SOP_FILE = os.path.join(DOCS_DIR, "SOP.md")

# --- Agent 模型路由配置 ---
# 你可以在这里根据实际效果随时调整
AGENT_CONFIG = {
    "architect_backend": {
        "provider": "codex",
        "model_id": "gpt-5.2",
        "temperature": 0.2
    },
    "architect_frontend": {
        "provider": "codex", # 或者根据你的喜好用 codex/qwen
        "model_id": "gpt-5.2",
        "temperature": 0.3
    },
    "task_broker": {
        "provider": "deepseek",
        "model_id": "deepseek-chat",
        "temperature": 0.1
    },
    "coder": {
        "provider": "qwen",
        "model_id": "qwen-coder-turbo",
        "temperature": 0.5
    },
    "reviewer": {
        "provider": "codex",
        "model_id": "gpt-5.2", # 建议使用稳定型号名
        "temperature": 0.1
    }
}
# --- 环境变量读取 (API Keys) ---
# 建议在终端通过 export 设置，不要硬编码在代码里
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")