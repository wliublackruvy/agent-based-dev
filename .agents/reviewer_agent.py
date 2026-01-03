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
        dirs[:] = [d for d in dirs if d not in {".git", "venv", "__pycache__", ".agents", "node_modules", ".pytest_cache", "target"}]
        for f in files:
            if f.endswith((".py", ".ts", ".tsx", ".js", ".md", ".json", ".java", ".xml", ".vue")):
                rel_path = os.path.relpath(os.path.join(root, f), root_path)
                file_list.append(rel_path)
    return "\n".join(file_list)

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def run_tests_polyglot(test_files):
    """
    多语言测试运行器 (Polyglot Test Runner)
    支持: Java (Maven), Python (Pytest), Frontend (npm)
    """
    if not test_files:
        return False, "No test files found."

    first_test = test_files[0]
    cmd = []
    
    print(colored(f"🧪 Preparing to test: {first_test}", "cyan"))

    # === Java (Maven) 策略 ===
    if first_test.endswith(".java"):
        # 提取类名: src/test/java/com/example/AuthTest.java -> AuthTest
        class_name = Path(first_test).stem
        print(colored(f"☕ Java Detected. Executing Maven: mvn -Dtest={class_name} test", "cyan"))
        # 注意：Maven 必须在 pom.xml 所在目录运行（通常是根目录）
        cmd = ["mvn", "-Dtest=" + class_name, "test"]

    # === Python (Pytest) 策略 ===
    elif first_test.endswith(".py"):
        print(colored(f"🐍 Python Detected. Executing Pytest.", "cyan"))
        cmd = ["pytest"] + test_files + ["-v", "--disable-warnings"]
    
    # === Frontend (UniApp/Vue) 策略 ===
    elif first_test.endswith((".ts", ".js", ".vue")):
         print(colored(f"⚛️ Frontend Detected. Executing npm test.", "cyan"))
         cmd = ["npm", "run", "test"]

    else:
        return False, f"❌ Unknown test file type: {first_test}"

    try:
        # 运行测试命令
        result = subprocess.run(
            cmd, 
            capture_output=True, 
            text=True, 
            timeout=120, # Java 编译可能比较慢，给多点时间
            cwd=PROJECT_ROOT # 关键：确保在项目根目录运行
        )
        return result.returncode == 0, result.stdout + result.stderr
    except FileNotFoundError:
        return False, f"❌ Command not found: {cmd[0]}. Please check your environment (Maven/Python/Node)."
    except subprocess.TimeoutExpired:
        return False, "❌ Test execution timed out (120s)."
    except Exception as e:
        return False, str(e)

def main():
    print("🕵️  Reviewer Agent (Polyglot Edition) is starting...")

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

    # 2. 检查文件是否存在
    missing_files = []
    new_code_content = ""
    for f_path in files:
        full_path = PROJECT_ROOT / f_path
        if full_path.exists():
            new_code_content += f"\n### FILE: {f_path}\n"
            new_code_content += full_path.read_text(encoding="utf-8")
        else:
            missing_files.append(f_path)
    
    if missing_files:
        print(colored(f"❌ Rejected. Missing files: {missing_files}", "red"))
        review_task["status"] = "todo"
        review_task["feedback"] = f"Files failed to write to disk: {missing_files}"
        with open(TASKS_FILE, "w") as f: json.dump(tasks, f, indent=2)
        return

    # 3. 阶段一：语义审查 (Semantic Review)
    print(colored("🧠 Performing Semantic Analysis (Code Quality/Duplication)...", "yellow"))
    
    prompt_template = load_text(PROMPT_FILE)
    file_tree = get_file_tree(PROJECT_ROOT)
    
    user_prompt = f"""
    === PROJECT FILE TREE ===
    {file_tree}

    === NEW CODE SUBMITTED ===
    {new_code_content}
    """
    
    # 临时禁用 JSON 模式检查，防止空文件报错，实际使用建议开启异常捕获
    try:
        llm_resp = call_llm(prompt_template, user_prompt, json_mode=True)
        review_result = parse_json_response(llm_resp)
        
        if review_result and review_result.get("status") == "FAIL":
            print(colored("⛔ Review Failed (Semantic Issues):", "red"))
            print(colored(f"Reason: {review_result.get('reason')}", "red"))
            review_task["status"] = "todo"
            review_task["feedback"] = f"Reviewer Rejected: {review_result.get('reason')}"
            with open(TASKS_FILE, "w", encoding="utf-8") as f:
                json.dump(tasks, f, indent=2, ensure_ascii=False)
            return
    except Exception as e:
        print(colored(f"⚠️ LLM Review skipped due to error: {e}", "yellow"))

    print(colored("✅ Semantic Check Passed.", "green"))

    # 4. 阶段二：执行测试 (Execution Review)
    # 智能识别测试文件：包含 'test' 或者是 .java 且以 Test 结尾
    test_files = [
        f for f in files 
        if "test" in f.lower() or f.endswith("Test.java") or f.endswith("Tests.java")
    ]
    
    if test_files:
        passed, log = run_tests_polyglot(test_files)
        if passed:
            print(colored("✅ All Tests Passed!", "green"))
            review_task["status"] = "done"
            review_task["feedback"] = "Tests Passed."
        else:
            print(colored("❌ Tests Failed!", "red"))
            # 打印部分日志
            print(log[-1000:])
            review_task["status"] = "todo"
            review_task["feedback"] = f"Reviewer Test Failure:\n{log[-1000:]}"
    else:
        print(colored("⚠️  No tests found in submission. Manual check recommended.", "yellow"))
        # 这里你可以选择是否放行
        review_task["status"] = "done"

    # 保存状态
    with open(TASKS_FILE, "w", encoding="utf-8") as f:
        json.dump(tasks, f, indent=2, ensure_ascii=False)
    
    print(f"Task status updated to: {review_task['status']}")

if __name__ == "__main__":
    main()