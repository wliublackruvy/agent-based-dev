import sys
import os
import json
import re
import subprocess
from pathlib import Path
from termcolor import colored

# === 路径配置 ===
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.join(current_dir, "..")) 
sys.path.append(current_dir)
from lib.llm import call_llm

PROJECT_ROOT = Path(os.getcwd())
DOCS_DIR = PROJECT_ROOT / "docs"
PRD_FILE = DOCS_DIR / "PRD.md"
TASKS_FILE = DOCS_DIR / "tasks.json"
PROMPT_FILE = PROJECT_ROOT / ".agents/prompts/coder_prompt.md"

MAX_RETRIES = 3

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def get_file_tree(root_path):
    """
    给 Coder 一双眼睛，让它看到现在的项目结构，防止盲目创建重复文件
    """
    file_list = []
    for root, dirs, files in os.walk(root_path):
        dirs[:] = [d for d in dirs if d not in {".git", "venv", "__pycache__", ".agents", "target", "node_modules"}]
        for f in files:
            if f.endswith((".java", ".py", ".ts", ".vue", ".xml")):
                rel_path = os.path.relpath(os.path.join(root, f), root_path)
                file_list.append(rel_path)
    return "\n".join(file_list)

def parse_response(text):
    """
    解析 LLM 的输出，支持 ### FILE: 和 ### DELETE:
    """
    files_to_create = {}
    files_to_delete = []

    # 按行分割处理，因为 DELETE 通常只有一行
    # 但为了兼容多行代码，我们还是用正则切分块
    
    # 1. 提取所有指令块
    # 匹配 ### (FILE|DELETE): 路径
    # 使用 split 分割
    token_pattern = re.compile(r'(### (?:FILE|DELETE): .+)')
    parts = token_pattern.split(text)
    
    # parts[0] 是开头废话，parts[1] 是标记，parts[2] 是内容，以此类推
    current_action = None
    current_path = None
    
    for part in parts:
        part = part.strip()
        if not part: continue

        if part.startswith("### FILE:"):
            current_action = "FILE"
            current_path = part.replace("### FILE:", "").strip()
        elif part.startswith("### DELETE:"):
            current_action = "DELETE"
            path = part.replace("### DELETE:", "").strip()
            files_to_delete.append(path)
        elif current_action == "FILE" and current_path:
            # 这是文件内容
            content = part
            if content.startswith("```"): content = content.split("\n", 1)[1]
            if content.endswith("```"): content = content.rsplit("```", 1)[0]
            files_to_create[current_path] = content.strip()
            current_action = None # 重置
            current_path = None
        # DELETE 后面没有内容块，所以不需要处理内容
            
    return files_to_create, files_to_delete

def run_tests(test_files):
    if not test_files: return False, "No test files found."
    
    first_test = test_files[0]
    cmd = []
    
    if first_test.endswith(".java"):
        class_name = Path(first_test).stem 
        print(colored(f"☕ Detect Java Test. Running: mvn -Dtest={class_name} test", "cyan"))
        cmd = ["mvn", "-Dtest=" + class_name, "test"]
    elif first_test.endswith(".py"):
        print(colored(f"🐍 Detect Python Test. Running: pytest {first_test}", "cyan"))
        cmd = ["pytest", first_test]
    elif first_test.endswith((".ts", ".js", ".vue")):
        print(colored("⚛️  Detect Frontend Test. Running: npm run test", "cyan"))
        cmd = ["npm", "run", "test"]
    else:
        return False, f"Unknown test file type: {first_test}"

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60, cwd=PROJECT_ROOT)
        return result.returncode == 0, result.stdout + result.stderr
    except Exception as e:
        return False, str(e)

def main():
    print("👷 Coder Agent (With Delete Powers) is ready...")

    if not TASKS_FILE.exists(): return

    with open(TASKS_FILE, "r") as f:
        tasks = json.load(f)

    target_task = None
    for t in tasks:
        if t.get("status") == "todo":
            target_task = t
            break
    
    if not target_task:
        print("🎉 No more TODO tasks!")
        return

    print(colored(f"🚀 Working on: {target_task['title']}", "green"))

    # 上下文准备
    prd_content = load_text(PRD_FILE)
    prompt_template = load_text(PROMPT_FILE)
    
    # === 关键新增：把当前文件树喂给 Coder ===
    # 这样它在写代码前就能看到 "com.example" 已经存在了
    file_tree = get_file_tree(PROJECT_ROOT)
    
    feedback = target_task.get("feedback", "")
    feedback_section = ""
    if feedback:
        print(colored(f"⚠️  Reviewer Feedback: {feedback}", "magenta"))
        feedback_section = f"\n=== CRITICAL FEEDBACK FROM REVIEWER ===\n{feedback}\n(If the feedback mentions duplicate files, use ### DELETE: to remove the incorrect ones.)\n"

    base_user_prompt = prompt_template.replace("{{task_type}}", target_task.get("type", "general")) \
                                      .replace("{{task_title}}", target_task["title"]) \
                                      .replace("{{task_desc}}", target_task["description"]) \
                                      .replace("{{acceptance_criteria}}", target_task.get("acceptance_criteria", "")) \
                                      .replace("{{prd_ref}}", target_task.get("prd_ref", "General")) \
                                      .replace("{{prd_content}}", prd_content) \
                                      .replace("{{existing_content}}", f"Current Project Structure:\n{file_tree}") # 把文件树注入上下文

    base_user_prompt += feedback_section

    current_prompt = base_user_prompt
    attempt = 0
    
    while attempt < MAX_RETRIES:
        attempt += 1
        print(colored(f"\n🔄 Attempt {attempt}/{MAX_RETRIES}...", "yellow"))
        
        raw_output = call_llm("You are a Senior Engineer.", current_prompt)
        
        # 解析输出
        files_to_create, files_to_delete = parse_response(raw_output)
        
        if not files_to_create and not files_to_delete:
            print(colored("❌ LLM output format error (No FILE or DELETE tags). Retrying...", "red"))
            continue

        # 1. 先执行删除操作
        if files_to_delete:
            print(colored(f"🗑️  Deleting {len(files_to_delete)} files...", "red"))
            for del_path in files_to_delete:
                full_del_path = PROJECT_ROOT / del_path
                if full_del_path.exists():
                    full_del_path.unlink() # 删除文件
                    print(f"   - Deleted: {del_path}")
                else:
                    print(f"   - Skipped (Not found): {del_path}")

        # 2. 再执行创建/更新操作
        saved_test_files = []
        for rel_path, content in files_to_create.items():
            full_path = PROJECT_ROOT / rel_path
            full_path.parent.mkdir(parents=True, exist_ok=True)
            with open(full_path, "w", encoding="utf-8") as f:
                f.write(content)
            
            if "test" in rel_path.lower() or rel_path.endswith("Test.java"):
                saved_test_files.append(rel_path)

        print(colored(f"💾 Created/Updated {len(files_to_create)} files.", "blue"))

        # 3. 运行测试
        # 只有在创建了新文件时才跑测试。如果只是纯删除，不需要跑测试（或者应该跑全量，这里简化处理）
        if files_to_create:
            if not saved_test_files:
                # 尝试找找现有的测试文件
                # 比如即使这次没写测试，可能之前已经有测试文件了
                print(colored("⚠️  No new test files. Looking for existing tests...", "yellow"))
                # 这里简单处理：如果没写测试，就跳过测试环节，直接进 Review
                break 
            
            passed, log = run_tests(saved_test_files)
            
            if passed:
                print(colored("✅ Tests Passed! Code is valid.", "green"))
                break
            else:
                print(colored("❌ Tests Failed! Asking LLM to fix...", "red"))
                error_feedback = f"\n\n=== TEST FAILURE ===\nLog:\n{log[-1000:]}\nFix the code."
                current_prompt = base_user_prompt + error_feedback
        else:
            # 只有删除操作，没写新代码
            print(colored("✅ Cleanup only. Task complete.", "green"))
            break

    # === 循环结束 ===
    target_task["file_path"] = ", ".join(files_to_create.keys()) # 只记录现在还在的文件
    target_task["status"] = "review" 
    # 清空 feedback，因为已经处理完了
    if "feedback" in target_task: del target_task["feedback"]
    
    with open(TASKS_FILE, "w", encoding="utf-8") as f:
        json.dump(tasks, f, indent=2, ensure_ascii=False)
    
    print("✅ Task marked as 'review'.")

if __name__ == "__main__":
    main()