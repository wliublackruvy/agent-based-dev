import sys
import os
import json
import time
import subprocess
from pathlib import Path
from termcolor import colored

# === 路径配置 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = Path(os.getcwd())
TASKS_FILE = PROJECT_ROOT / "docs" / "tasks.json"

CODER_SCRIPT = f"{current_dir}/coder_agent.py"
REVIEWER_SCRIPT = f"{current_dir}/reviewer_agent.py"

MAX_GLOBAL_LOOPS = 50  # 防止无限循环消耗 Token

def load_tasks():
    if not TASKS_FILE.exists(): return []
    with open(TASKS_FILE, "r") as f:
        return json.load(f)

def run_agent(script_path):
    """运行指定的 Agent 脚本"""
    try:
        # 使用 subprocess 调用，这样可以实时看到 Agent 的彩色输出
        result = subprocess.run(
            [sys.executable, script_path],
            cwd=PROJECT_ROOT
        )
        return result.returncode == 0
    except Exception as e:
        print(colored(f"❌ System Error running agent: {e}", "red"))
        return False

def main():
    print(colored("🏗️  The Factory is Starting...", "magenta", attrs=["bold"]))
    
    loop_count = 0
    while loop_count < MAX_GLOBAL_LOOPS:
        loop_count += 1
        print(colored(f"\n🔄 Global Loop {loop_count}/{MAX_GLOBAL_LOOPS}", "cyan"))
        
        tasks = load_tasks()
        if not tasks:
            print("❌ No tasks found.")
            break

        # 统计状态
        todo_count = sum(1 for t in tasks if t.get("status") == "todo")
        review_count = sum(1 for t in tasks if t.get("status") == "review")
        done_count = sum(1 for t in tasks if t.get("status") == "done")
        
        print(f"📊 Status: TODO={todo_count} | REVIEW={review_count} | DONE={done_count}")

        if todo_count == 0 and review_count == 0:
            print(colored("\n🎉🎉🎉 ALL TASKS COMPLETED! THE PROJECT IS READY! 🎉🎉🎉", "green", attrs=["bold"]))
            break

        # === 调度逻辑 ===
        
        # 优先处理 Review (把做完的先验了，防止堆积)
        if review_count > 0:
            print(colored("👉 Dispatching Reviewer Agent...", "blue"))
            run_agent(REVIEWER_SCRIPT)
            continue # 跑完一次 Review 回头重新检查状态

        # 其次处理 Todo (让 Coder 写代码)
        if todo_count > 0:
            # 找到当前正在进行的任务
            current_task = next((t for t in tasks if t.get("status") == "todo"), None)
            if current_task:
                print(colored(f"👉 Dispatching Coder Agent for: {current_task['title']}", "yellow"))
                run_agent(CODER_SCRIPT)
            continue

        time.sleep(1) # 防止空转

    if loop_count >= MAX_GLOBAL_LOOPS:
        print(colored("💀 Max loops reached. Please check if Agents are stuck in a feedback loop.", "red"))

if __name__ == "__main__":
    main()