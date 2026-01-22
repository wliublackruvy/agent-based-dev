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
        "model_id": "gpt-5",
        "temperature": 0.2
    },
    "architect_frontend": {
        "provider": "codex", # 或者根据你的喜好用 codex/qwen
        "model_id": "gpt-5",
        "temperature": 0.3
    },
    "task_broker": {
        "provider": "deepseek",
        "model_id": "deepseek-chat",
        "temperature": 0.1
    },
    "coder": {
        "provider": "codex",
        "model_id": "glm-4.7",
        "temperature": 0.1
    },
    "test_agent": {
        "provider": "deepseek",  # 建议集成测试用逻辑强的模型
        "model_id": "deepseek-chat",
        "temperature": 0.2
    },
    "reviewer": {
        "provider": "deepseek",
        "model_id": "deepseek-chat", # 建议使用稳定型号名
        "temperature": 0.1
    }
}
# --- 环境变量读取 (API Keys) ---
# 建议在终端通过 export 设置，不要硬编码在代码里
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")

# --- 运行参数 ---
try:
    DEEPSEEK_TIMEOUT = int(os.getenv("DEEPSEEK_TIMEOUT", "180"))
except ValueError:
    DEEPSEEK_TIMEOUT = 180  # fallback when user passes a non-numeric value
