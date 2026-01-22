import sys
import os
import re
import argparse
import subprocess
from pathlib import Path
from termcolor import colored

# === 路径配置 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(current_dir, "..")) 
sys.path.append(current_dir)

from lib.llm import call_llm, parse_json_response
from config import DOCS_DIR

PROJECT_ROOT = Path(os.getcwd())
PROMPT_FILE = PROJECT_ROOT / "agents/prompts/reviewer_prompt.md"
TASK_FILES = {
    "be": PROJECT_ROOT / "docs/TASKS_BE.md",
    "fe": PROJECT_ROOT / "docs/TASKS_FE.md"
}

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def save_file(path, content):
    path.write_text(content, encoding="utf-8")

def get_file_tree(root_path):
    """获取项目文件结构，排除无关文件夹"""
    file_list = []
    for root, dirs, files in os.walk(root_path):
        dirs[:] = [d for d in dirs if d not in {".git", "venv", "__pycache__", ".agents", "node_modules", ".pytest_cache", "target", ".idea", ".vscode"}]
        for f in files:
            if f.endswith((".py", ".ts", ".tsx", ".js", ".md", ".json", ".java", ".xml", ".vue", ".yml", ".sql")):
                rel_path = os.path.relpath(os.path.join(root, f), root_path)
                file_list.append(rel_path)
    return "\n".join(file_list)

def find_review_task(role):
    """从 Markdown 文件中查找状态为 [review] 的任务"""
    file_path = TASK_FILES.get(role)
    if not file_path or not file_path.exists():
        print(colored(f"❌ Task file not found for role: {role}", "red"))
        return None

    content = load_text(file_path)
    # 匹配格式: - [review] Task-ID: Title | Detail | Ref: ... | Feedback: ...
    pattern = r"- \[(review)\] ((Task-\w+-\d+): (.*?) \| (.*?) \| Ref: (.*?) \| Feedback: (.*))"
    match = re.search(pattern, content)
    
    if match:
        status, full_line, task_id, title, detail, ref, feedback = match.groups()
        return {
            "id": task_id,
            "title": title,
            "detail": detail,
            "ref": ref,
            "feedback": feedback,
            "role": role,
            "raw_line": f"- [{status}] {full_line}"
        }
    return None

def update_task_status(role, task_id, new_status, feedback):
    """更新 Markdown 文件中的任务状态和反馈"""
    file_path = TASK_FILES.get(role)
    if not file_path: return

    content = load_text(file_path)
    lines = content.splitlines()
    new_lines = []
    updated = False

    for line in lines:
        if task_id in line:
            # 替换状态
            line = re.sub(r"\[.*?\]", f"[{new_status}]", line, count=1)
            # 替换反馈 (Feedback: 后面的所有内容直到行尾)
            # 注意：如果反馈包含换行符，Markdown 表格/列表通常要求单行，这里假设单行
            clean_feedback = feedback.replace("\n", " ").replace("\r", "")
            if "Feedback:" in line:
                line = re.sub(r"Feedback:.*", f"Feedback: {clean_feedback}", line)
            else:
                line += f" | Feedback: {clean_feedback}"
            updated = True
            print(colored(f"📝 Updated {task_id} -> [{new_status}]", "cyan"))
        new_lines.append(line)
    
    if updated:
        save_file(file_path, "\n".join(new_lines))

def run_tests_polyglot(role):
    """
    运行测试
    BE -> Maven Verify
    FE -> npm run test
    """
    print(colored(f"🧪 Executing Tests for Role: {role.upper()}", "cyan"))
    
    cmd = []
    if role == "be":
        # 运行集成测试，跳过单元测试以加快速度，或者全跑
        cmd = ["mvn", "verify", "-B"] 
    elif role == "fe":
        cmd = ["npm", "run", "test"]
    else:
        return False, "Unknown role"

    try:
        # 确保在项目根目录运行
        result = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True, 
            timeout=300, 
            cwd=PROJECT_ROOT 
        )
        return result.returncode == 0, result.stdout + result.stderr
    except Exception as e:
        return False, str(e)

def main():
    parser = argparse.ArgumentParser(description="Reviewer Agent")
    parser.add_argument("-r", "--role", choices=["be", "fe"], required=True, help="Role (be/fe)")
    parser.add_argument("-t", "--task", help="Specific Task ID (optional, defaults to first [review] task)")
    args = parser.parse_args()

    print("🕵️  Reviewer Agent starting...")

    # 1. 查找任务
    task = find_review_task(args.role)
    if not task:
        print(colored("💤 No tasks waiting for review.", "yellow"))
        return

    if args.task and task["id"] != args.task:
        print(colored(f"⚠️  Found {task['id']} but you requested {args.task}. Proceeding with found task.", "yellow"))

    print(colored(f"🔍 Inspecting: {task['id']} - {task['title']}", "blue"))

    # 2. 获取代码上下文 (Code Snapshot)
    # Reviewer 需要看最近修改的文件。由于 Git Diff 比较复杂，
    # 我们这里简单读取文件树，并让 LLM 结合项目结构进行‘盲审’或者全量审查
    # 更好的做法是 Coder 应该把修改的文件路径记录下来，但目前架构简单，
    # 我们让 LLM 审查所有相关代码，或者我们假设最近修改的文件是最重要的。
    # 为了简化，我们读取 src 下的核心代码传给 LLM。
    
    # 优化：只读取 src 目录下的代码，避免太长
    src_dir = PROJECT_ROOT / "src"
    code_content = ""
    file_count = 0
    MAX_CHARS = 50000 # 限制 Token
    
    for root, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith((".java", ".ts", ".vue", ".xml")):
                p = Path(os.path.join(root, f))
                # 简单过滤：只看最近修改的？或者看全部。
                # 这里暂时读取全部，直到 Token 上限
                txt = load_text(p)
                if len(code_content) + len(txt) < MAX_CHARS:
                    code_content += f"\n### FILE: {os.path.relpath(p, PROJECT_ROOT)}\n{txt}\n"
                    file_count += 1
                else:
                    break
    
    print(colored(f"📦 Loaded {file_count} source files for Semantic Review.", "magenta"))

    # 3. 阶段一：语义审查 (Semantic Review)
    print(colored("🧠 Performing Semantic Analysis...", "yellow"))
    
    prompt_template = load_text(PROMPT_FILE)
    file_tree = get_file_tree(PROJECT_ROOT)
    
    user_prompt = f"""
    === PROJECT FILE TREE ===
    {file_tree}

    === CURRENT CODEBASE CONTENT (Sample) ===
    {code_content}
    
    Task ID: {task['id']}
    Task Description: {task['title']} - {task['detail']}
    """
    
    try:
        # JSON 模式调用
        llm_resp = call_llm(prompt_template, user_prompt, json_mode=True)
        review_result = parse_json_response(llm_resp)
        
        if review_result and review_result.get("status") == "FAIL":
            reason = review_result.get('reason', 'Unknown semantic issue')
            print(colored("⛔ Review Failed (Semantic Issues):", "red"))
            print(colored(f"Reason: {reason}", "red"))
            update_task_status(args.role, task["id"], "todo", f"Reviewer Rejected: {reason}")
            return
            
    except Exception as e:
        print(colored(f"⚠️ LLM Review skipped/error: {e}", "yellow"))

    print(colored("✅ Semantic Check Passed.", "green"))

    # 4. 阶段二：执行测试 (Execution Review)
    passed, log = run_tests_polyglot(args.role)
    
    if passed:
        print(colored("✅ All Tests Passed!", "green"))
        update_task_status(args.role, task["id"], "done", "Passed Review & Tests")
    else:
        print(colored("❌ Tests Failed!", "red"))
        # 提取关键错误日志
        core_error = log[-2000:] # 取最后2000字符
        print(core_error)
        update_task_status(args.role, task["id"], "todo", f"Test Failed. Log: {core_error[:200]}...")

if __name__ == "__main__":
    main()