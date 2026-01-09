import os
from termcolor import colored
from agents.lib.llm import call_llm_for_agent
from agents.config import ACTIONS, SCOPE, PRD_FILE

# 路径常量
DESIGN_DIR = "docs/design"
BE_DESIGN = os.path.join(DESIGN_DIR, "backend.md")
FE_DESIGN = os.path.join(DESIGN_DIR, "frontend.md")
BE_TASKS = "docs/TASKS_BE.md"
FE_TASKS = "docs/TASKS_FE.md"

def load_file(path):
    return open(path, 'r', encoding='utf-8').read() if os.path.exists(path) else ""

def save_file(path, content):
    os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def run_pipeline():
    print(colored("🛠️  Agent Workflow Started...", "blue", attrs=["bold"]))
    prd = load_file(PRD_FILE)

    # --- 第一阶段：设计生成 (Designer) ---
    if ACTIONS["GENERATE_DESIGN"]:
        if SCOPE["BACKEND"]:
            print(colored("\n[1/2] Updating Backend Design...", "cyan"))
            prompt = load_file("agents/prompts/architect_backend_prompt.md")
            # 传入当前设计实现增量更新建议
            res = call_llm_for_agent("architect_backend", prompt, f"PRD: {prd}\nEXISTING_DESIGN: {load_file(BE_DESIGN)}")
            save_file(BE_DESIGN, res)
            print(colored("✅ Backend Design updated. Please review docs/design/backend.md", "green"))

        if SCOPE["FRONTEND"]:
            print(colored("\n[2/2] Updating Frontend Design...", "cyan"))
            prompt = load_file("agents/prompts/architect_frontend_prompt.md")
            be_context = load_file(BE_DESIGN) # 参考后端设计以保持 API 一致
            res = call_llm_for_agent("architect_frontend", prompt, f"PRD: {prd}\nBE_DESIGN: {be_context}\nEXISTING_DESIGN: {load_file(FE_DESIGN)}")
            save_file(FE_DESIGN, res)
            print(colored("✅ Frontend Design updated. Please review docs/design/frontend.md", "green"))
        
        print(colored("\n⚠️  Design phase completed. Please manually verify design files before syncing tasks.", "yellow"))

    # --- 第二阶段：任务同步 (Task Broker) ---
    if ACTIONS["SYNC_TASKS"]:
        broker_prompt = load_file("agents/prompts/task_broker_prompt.md")
        
        if SCOPE["BACKEND"]:
            print(colored("\n[Broker] Syncing Backend Tasks...", "magenta"))
            design = load_file(BE_DESIGN)
            existing_tasks = load_file(BE_TASKS)
            # Broker 接收当前 Design 和当前 Task，进行 Diff 同步
            res = call_llm_for_agent("task_broker", broker_prompt, f"ROLE: BE\nDESIGN: {design}\nCURRENT_TASKS: {existing_tasks}")
            save_file(BE_TASKS, res)
            print(colored("✅ TASKS_BE.md synchronized.", "green"))

        if SCOPE["FRONTEND"]:
            print(colored("\n[Broker] Syncing Frontend Tasks...", "magenta"))
            design = load_file(FE_DESIGN)
            existing_tasks = load_file(FE_TASKS)
            res = call_llm_for_agent("task_broker", broker_prompt, f"ROLE: FE\nDESIGN: {design}\nCURRENT_TASKS: {existing_tasks}")
            save_file(FE_TASKS, res)
            print(colored("✅ TASKS_FE.md synchronized.", "green"))

if __name__ == "__main__":
    run_pipeline()