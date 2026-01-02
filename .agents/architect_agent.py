import sys
import os
import json
from pathlib import Path
from termcolor import colored

# === 路径黑魔法 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(current_dir, "..")) 
sys.path.append(current_dir)

from lib.llm import call_llm, parse_json_response

# 配置路径
PROJECT_ROOT = Path(os.getcwd())
DOCS_DIR = PROJECT_ROOT / "docs"
PRD_FILE = DOCS_DIR / "PRD.md"
TASKS_FILE = DOCS_DIR / "tasks.json"
PROMPT_FILE = PROJECT_ROOT / ".agents/prompts/architect_prompt.md"

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def main():
    print(colored("🏗️  Architect Agent: Analyzing PRD & Planning Tasks...", "cyan"))

    # 1. 检查 PRD
    if not PRD_FILE.exists():
        print(colored("❌ Error: docs/PRD.md not found.", "red"))
        return

    prd_content = load_text(PRD_FILE)
    system_prompt = load_text(PROMPT_FILE)

    # 2. 构造 Prompt
    # 我们告诉它：基于这个 PRD，给我生成完整的 JSON 列表
    user_prompt = f"""
    === PRODUCT REQUIREMENT DOCUMENT ===
    {prd_content}

    === INSTRUCTION ===
    Generate the full engineering task list (Frontend, Backend, Algorithms) based on the rules in the system prompt.
    Ensure the JSON format is strict.
    """

    # 3. 调用 AI
    # 注意：DeepSeek/GPT-4 生成长列表可能会比较慢，请耐心等待
    print(colored("⏳ Thinking (Structuring Engineering Tasks)...", "yellow"))
    response = call_llm(system_prompt, user_prompt, json_mode=True)
    
    # 4. 解析与保存
    new_plan = parse_json_response(response)

    if new_plan:
        # 确保它是一个列表
        if isinstance(new_plan, dict) and "tasks" in new_plan:
            new_plan = new_plan["tasks"] # 容错处理，如果 AI 包了一层
        
        if not isinstance(new_plan, list):
            print(colored("❌ Error: AI did not return a list of tasks.", "red"))
            print(new_plan)
            return

        # 保存到 tasks.json
        with open(TASKS_FILE, "w", encoding="utf-8") as f:
            json.dump(new_plan, f, indent=2, ensure_ascii=False)
        
        print(colored(f"✅ Architecture Plan Generated! {len(new_plan)} tasks created.", "green"))
        print(f"📂 Saved to: {TASKS_FILE}")
        
        # 打印前几个任务预览
        print("\n--- Task Preview ---")
        for i, task in enumerate(new_plan[:3]):
            print(f"[{task['type'].upper()}] {task['title']} ({task['priority']})")
    else:
        print(colored("❌ Failed to generate plan.", "red"))

if __name__ == "__main__":
    main()