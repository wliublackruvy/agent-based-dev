import os
import re
import sys
import subprocess
import argparse
from pathlib import Path
from termcolor import colored

# 基础路径配置
sys.path.append(os.getcwd())
from agents.lib.llm import call_llm_for_agent
from agents.config import DOCS_DIR, PRD_FILE

TASK_FILES = {"be": os.path.join(DOCS_DIR, "TASKS_BE.md"), "fe": os.path.join(DOCS_DIR, "TASKS_FE.md")}
DESIGN_FILES = {"be": os.path.join(DOCS_DIR, "design/backend.md"), "fe": os.path.join(DOCS_DIR, "design/frontend.md")}
MAX_RETRIES = 3

def load_file(path):
    return Path(path).read_text(encoding='utf-8') if os.path.exists(path) else ""

def save_file(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')

def get_current_code_context():
    """
    扫描并读取当前项目已生成的代码，作为修复上下文。
    仅读取与业务逻辑和测试相关的核心目录。
    """
    context_str = "\n=== CURRENT PROJECT CODE SNAPSHOT ===\n"
    search_dirs = ["src/main/java/com/mingyu/app", "src/test/java/com/mingyu/app", "src/pages"]
    found = False
    for s_dir in search_dirs:
        if not os.path.exists(s_dir): continue
        for root, _, files in os.walk(s_dir):
            for file in files:
                if file.endswith((".java", ".vue", ".yml", ".xml")):
                    path = os.path.join(root, file)
                    content = load_file(path)
                    context_str += f"\n--- FILE: {path} ---\n{content}\n"
                    found = True
    return context_str if found else ""

def extract_key_error(log_content):
    """
    智能切片：从长 Stack Trace 中提取最核心的错误信息
    """
    # 查找 Caused by, Compilation failure, 或 AssertionFailedError
    lines = log_content.splitlines()
    key_lines = []
    for i, line in enumerate(lines):
        if any(keyword in line for keyword in ["Compilation failure", "Caused by:", "AssertionFailedError", "error:"]):
            # 提取关键词前后各 10 行
            start = max(0, i - 5)
            end = min(len(lines), i + 15)
            key_lines.append("\n".join(lines[start:end]))
    
    if not key_lines:
        return log_content[:1000] # 如果没搜到关键词，回退到前1000字符
    return "\n...[SNIP]...\n".join(key_lines)

def update_task_status(task_id, role, status, feedback="None"):
    path = TASK_FILES[role]
    if not os.path.exists(path): return
    lines = load_file(path).splitlines()
    new_lines = []
    for line in lines:
        if task_id in line:
            line = re.sub(r"\[.*?\]", f"[{status}]", line, count=1)
            line = re.sub(r"Feedback:.*", f"Feedback: {feedback}", line)
        new_lines.append(line)
    save_file(path, "\n".join(new_lines))

def parse_and_apply(text):
    files = re.findall(r"### FILE:\s*(.*?)\n(.*?)(?=\n### FILE:|\n### DELETE:|$)", text, re.DOTALL)
    for path, code in files:
        save_file(path.strip(), code.strip())
        print(colored(f"💾 Applied: {path.strip()}", "blue"))
    
    deletes = re.findall(r"### DELETE:\s*(.*)", text)
    for path in deletes:
        p = Path(path.strip())
        if p.exists():
            p.unlink()
            print(colored(f"🗑️ Deleted: {p}", "red"))

def run_all_tests(role):
    # Phase 1: Unit
    print(colored("🧪 Phase 1: Running Unit Tests...", "magenta"))
    unit_cmd = ["mvn", "test"] if role == "be" else ["npm", "run", "test:unit"]
    u_res = subprocess.run(unit_cmd, capture_output=True, text=True, timeout=120)
    if u_res.returncode != 0:
        return False, u_res.stdout + u_res.stderr

    # Phase 2: Integration
    print(colored("🧪 Phase 2: Running Integration Tests...", "cyan"))
    it_cmd = ["mvn", "verify", "-DskipUnitTests=true"] if role == "be" else ["npm", "run", "test:integration"]
    i_res = subprocess.run(it_cmd, capture_output=True, text=True, timeout=180)
    if i_res.returncode != 0:
        return False, i_res.stdout + i_res.stderr
    
    return True, "All tests passed!"

def run_coder(task_id, role, auto_confirm=False, debug=False):
    task_content = load_file(TASK_FILES[role])
    pattern = rf"- \[(.*?)\] ({task_id}: (.*?) \| (.*?) \| Ref: (.*?) \| Feedback: (.*))"
    match = re.search(pattern, task_content)
    if not match: return print(colored(f"❌ Task {task_id} not found", "red"))
    
    status, _, title, detail, ref, feedback = match.groups()
    design = load_file(DESIGN_FILES[role])
    prd = load_file(PRD_FILE)
    prompt_tmpl = load_file("agents/prompts/coder_prompt.md")

    attempt = 0
    current_fb = feedback
    
    while attempt < MAX_RETRIES:
        attempt += 1
        print(colored(f"\n🛠️  Attempt {attempt}/{MAX_RETRIES} for {task_id}", "cyan", attrs=["bold"]))
        
        # 核心改进：如果是重试，或者任务已有反馈（被Review拒绝），注入当前代码快照
        code_snapshot = ""
        if attempt > 1 or (current_fb and current_fb != "None"):
            code_snapshot = get_current_code_context()
            print(colored("🔍 Context: Injected current code snapshot for incremental fix.", "dark_grey"))

        user_input = (prompt_tmpl
                      .replace("{{task_id}}", task_id)
                      .replace("{{task_title}}", title)
                      .replace("{{task_detail}}", detail)
                      .replace("{{design_content}}", design)
                      .replace("{{prd_content}}", prd)
                      .replace("{{feedback}}", current_fb))
        
        # 将代码快照追加到 Prompt 尾部，不干扰原有的变量替换逻辑
        full_user_input = user_input + code_snapshot
        
        if debug:
            print(colored("\n" + "="*20 + " DEBUG: LLM INPUT " + "="*20, "magenta"))
            print(full_user_input)
            print(colored("="*58 + "\n", "magenta"))

        output = call_llm_for_agent("coder", "You are a Senior Implementation Engineer.", full_user_input)
        
        if not auto_confirm:
            print(colored("\n--- AI Proposed Changes ---", "yellow"))
            print(output.split("### FILE:")[0])
            if input(colored("\nApply and Test? (y/n/skip): ", "green")).lower() != 'y': break

        parse_and_apply(output)
        success, log = run_all_tests(role)

        if success:
            print(colored(f"✅ PASSED! {task_id} is ready for review.", "green", attrs=["bold"]))
            update_task_status(task_id, role, "review", "None")
            return
        else:
            print(colored(f"❌ FAILED on Attempt {attempt}.", "red"))
            # 将核心错误提取出来给下一次尝试
            core_error = extract_key_error(log)
            current_fb = f"ERROR LOG SNIPPET: {core_error}"
            
            if attempt == MAX_RETRIES:
                update_task_status(task_id, role, "todo", f"Failed after {MAX_RETRIES} attempts. See local logs.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-t", "--task", required=True)
    parser.add_argument("-r", "--role", choices=["be", "fe"], required=True)
    parser.add_argument("-y", "--yes", action="store_true")
    parser.add_argument("-d", "--debug", action="store_true")
    args = parser.parse_args()
    run_coder(args.task, args.role, auto_confirm=args.yes, debug=args.debug)