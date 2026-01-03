import sys
import os
import json
from pathlib import Path
from termcolor import colored

# === 路径配置 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(current_dir, "..")) 
sys.path.append(current_dir)

from lib.llm import call_llm, parse_json_response

PROJECT_ROOT = Path(os.getcwd())
DOCS_DIR = PROJECT_ROOT / "docs"
PRD_FILE = DOCS_DIR / "PRD.md"
TASKS_FILE = DOCS_DIR / "tasks.json"
PROMPT_FILE = PROJECT_ROOT / ".agents/prompts/architect_prompt.md"

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def get_existing_tasks_summary():
    """
    获取现有任务的 ID 和标题，帮助 LLM 保持 ID 稳定。
    如果文件不存在或解析失败，返回 "None"。
    """
    if not TASKS_FILE.exists():
        return "None"
    try:
        with open(TASKS_FILE, "r", encoding="utf-8") as f:
            tasks = json.load(f)
        summary = []
        for t in tasks:
            summary.append(f"- ID: {t.get('id')} | Title: {t.get('title')}")
        return "\n".join(summary)
    except:
        return "None"

def merge_tasks(old_tasks, new_tasks):
    """
    智能合并逻辑：
    1. 以新生成的任务 (new_tasks) 为基准（Source of Truth）。
    2. 如果 ID 匹配，保留旧任务的运行时状态 (status, file_path, feedback)。
    3. 如果 ID 不在旧列表中，视为新增。
    """
    merged_list = []
    
    # 建立旧任务的快速查找表
    old_map = {t['id']: t for t in old_tasks}
    
    print(colored(f"\n🔄 Merging tasks...", "cyan"))
    
    for new_t in new_tasks:
        tid = new_t['id']
        
        if tid in old_map:
            # === 任务已存在：执行更新合并 ===
            old_t = old_map[tid]
            print(f"   - Updating existing task: {tid}")
            
            # 复制新任务的定义（Title, Description, AC 等）
            final_task = new_t.copy()
            
            # 恢复之前的运行时状态
            final_task['status'] = old_t.get('status', 'todo')
            final_task['file_path'] = old_t.get('file_path', '')
            
            # 如果之前有 feedback 且还没修好，也保留
            if 'feedback' in old_t:
                final_task['feedback'] = old_t['feedback']
                
            merged_list.append(final_task)
        else:
            # === 任务不存在：新增 ===
            print(f"   - Adding NEW task: {tid}")
            new_t['status'] = 'todo' # 新任务默认为 todo
            merged_list.append(new_t)
            
    return merged_list

def main():
    print(colored("🏗️  Architect Agent: Analyzing PRD & Syncing Tasks...", "cyan"))

    if not PRD_FILE.exists():
        print(colored("❌ Error: docs/PRD.md not found.", "red"))
        return

    prd_content = load_text(PRD_FILE)
    system_prompt = load_text(PROMPT_FILE)
    
    # === 关键步骤 1：获取现有任务摘要 ===
    existing_summary = get_existing_tasks_summary()

    # 加载完整的旧任务列表用于后续合并
    old_tasks = []
    if TASKS_FILE.exists():
        try:
            with open(TASKS_FILE, "r") as f:
                old_tasks = json.load(f)
        except:
            old_tasks = []

    # === 关键步骤 2：注入现有 ID 到 Prompt ===
    user_prompt = f"""
    === PRODUCT REQUIREMENT DOCUMENT (LATEST) ===
    {prd_content}

    === EXISTING TASKS (FOR ID CONSISTENCY) ===
    {existing_summary}

    === INSTRUCTION ===
    Generate the updated task list based on the PRD.
    - If a requirement matches an Existing Task ID, YOU MUST REUSE THAT ID.
    - If it's a new requirement, use a new ID.
    - Do not output tasks that are no longer relevant to the PRD.
    """

    print(colored("⏳ Thinking (Structuring Engineering Tasks)...", "yellow"))
    response = call_llm(system_prompt, user_prompt, json_mode=True)
    
    new_plan = parse_json_response(response)

    if new_plan:
        # 兼容性处理：防止 LLM 包了一层 "tasks": [...]
        if isinstance(new_plan, dict) and "tasks" in new_plan:
            new_plan = new_plan["tasks"]
        
        if not isinstance(new_plan, list):
            print(colored("❌ Error: AI did not return a list of tasks.", "red"))
            return

        # === 关键步骤 3：执行智能合并 ===
        final_tasks = merge_tasks(old_tasks, new_plan)

        # 保存结果
        with open(TASKS_FILE, "w", encoding="utf-8") as f:
            json.dump(final_tasks, f, indent=2, ensure_ascii=False)
        
        print(colored(f"✅ Architecture Plan Synced! Total tasks: {len(final_tasks)}", "green"))
        
        # 简单的差异统计
        old_ids = set(t['id'] for t in old_tasks)
        new_ids = set(t['id'] for t in final_tasks)
        added = new_ids - old_ids
        removed = old_ids - new_ids
        
        if added: print(f"   ➕ Added: {added}")
        if removed: print(f"   ➖ Removed: {removed}")
        
    else:
        print(colored("❌ Failed to generate plan.", "red"))

if __name__ == "__main__":
    main()