import sys
import os
import json
import subprocess
from pathlib import Path
from termcolor import colored

# === 路径配置 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(current_dir, "..")) 
sys.path.append(current_dir)

from lib.llm import call_llm, parse_json_response

PROJECT_ROOT = Path(os.getcwd())
TASKS_FILE = DOCS_DIR = PROJECT_ROOT / "docs" / "tasks.json"
PROMPT_FILE = PROJECT_ROOT / ".agents/prompts/reviewer_prompt.md"

def get_file_tree(root_path):
    """获取项目文件结构，排除无关文件夹"""
    file_list = []
    for root, dirs, files in os.walk(root_path):
        # 排除项
        dirs[:] = [d for d in dirs if d not in {".git", "venv", "__pycache__", ".agents", "node_modules", ".pytest_cache"}]
        for f in files:
            if f.endswith((".py", ".ts", ".tsx", ".js", ".md", ".json")):
                rel_path = os.path.relpath(os.path.join(root, f), root_path)
                file_list.append(rel_path)
    return "\n".join(file_list)

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def run_pytest(test_files):
    """运行测试"""
    print(colored(f"🧪 Running tests: {test_files}", "cyan"))
    cmd = ["pytest"] + test_files + ["-v", "--disable-warnings"]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return result.returncode == 0, result.stdout + result.stderr
    except Exception as e:
        return False, str(e)

def main():
    print("🕵️  Reviewer Agent (with De-duplication Check) is starting...")

    if not TASKS_FILE.exists(): return

    with open(TASKS_FILE, "r") as f:
        tasks = json.load(f)

    # 1. 找到 Review 任务
    review_task = None
    for t in tasks:
        if t.get("status") == "review":
            review_task = t
            break
    
    if not review_task:
        print("💤 Nothing to review.")
        return

    print(colored(f"🔍 Inspecting: {review_task['title']}", "blue"))
    files_str = review_task.get("file_path", "")
    files = [f.strip() for f in files_str.split(",")] if files_str else []

    # 2. 准备数据：读取新生成的代码内容
    new_code_content = ""
    for f_path in files:
        full_path = PROJECT_ROOT / f_path
        if full_path.exists():
            new_code_content += f"\n### FILE: {f_path}\n"
            new_code_content += full_path.read_text(encoding="utf-8")
        else:
            print(colored(f"❌ Missing file: {f_path}", "red"))
            review_task["status"] = "todo" # 文件都不全，直接打回
            return

    # 3. 阶段一：Semantic Review (查重/查复用)
    print(colored("🧠 Performing Semantic Analysis (Duplication Check)...", "yellow"))
    
    prompt_template = load_text(PROMPT_FILE)
    file_tree = get_file_tree(PROJECT_ROOT)
    
    user_prompt = f"""
    === PROJECT FILE TREE ===
    {file_tree}

    === NEW CODE SUBMITTED ===
    {new_code_content}
    """
    
    # 调用 LLM 判断
    llm_resp = call_llm(prompt_template, user_prompt, json_mode=True)
    review_result = parse_json_response(llm_resp)

    if not review_result:
        print(colored("❌ Reviewer Brain Malfunction (JSON Error). Skipping...", "red"))
        # 这种情况下你可以选择跳过或者打回，为了安全我们先打回
        return 

    if review_result.get("status") == "FAIL":
        print(colored("⛔ Review Failed (Semantic Issues):", "red"))
        print(colored(f"Reason: {review_result.get('reason')}", "red"))
        
        # 打回 Coder 重做
        review_task["status"] = "todo"
        # 这里的 feedback 以后可以传给 Coder Prompt
        review_task["feedback"] = f"Reviewer Rejected: {review_result.get('reason')}"
        
        with open(TASKS_FILE, "w", encoding="utf-8") as f:
            json.dump(tasks, f, indent=2, ensure_ascii=False)
        return
    
    print(colored("✅ Semantic Check Passed! No obvious duplicates.", "green"))

    # 4. 阶段二：Execution Review (运行测试)
    test_files = [f for f in files if "test" in f.lower() or f.endswith("_test.py")]
    
    if test_files:
        passed, log = run_pytest(test_files)
        if passed:
            print(colored("✅ All Tests Passed!", "green"))
            review_task["status"] = "done"
            review_task["feedback"] = "Good job."
        else:
            print(colored("❌ Tests Failed!", "red"))
            print(log[-500:])
            review_task["status"] = "todo"
            review_task["feedback"] = f"Tests failed: {log[-500:]}"
    else:
        print(colored("⚠️  No tests found. Proceeding with caution.", "yellow"))
        review_task["status"] = "done"

    # 保存最终状态
    with open(TASKS_FILE, "w", encoding="utf-8") as f:
        json.dump(tasks, f, indent=2, ensure_ascii=False)
    
    print(f"Task status updated to: {review_task['status']}")

if __name__ == "__main__":
    main()