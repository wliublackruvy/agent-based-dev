import os
import re
import sys
import subprocess
import argparse
from pathlib import Path
from termcolor import colored

# 确保路径正确
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

def update_task_status(task_id, role, status, feedback="None"):
    """更新 Markdown 任务行状态与反馈"""
    path = TASK_FILES[role]
    if not os.path.exists(path): return
    
    lines = load_file(path).splitlines()
    new_lines = []
    for line in lines:
        if task_id in line:
            # 替换状态部分 [todo] -> [review]
            line = re.sub(r"\[.*?\]", f"[{status}]", line, count=1)
            # 替换反馈部分
            line = re.sub(r"Feedback:.*", f"Feedback: {feedback}", line)
        new_lines.append(line)
    save_file(path, "\n".join(new_lines))

def parse_and_apply(text):
    """解析 AI 输出的 ### FILE 和 ### DELETE 指令"""
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

def run_tests(role):
    """执行单元测试并捕获日志"""
    print(colored("🧪 Running Tests...", "magenta"))
    cmd = ["mvn", "test"] if role == "be" else ["npm", "run", "test:unit"]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        # 合并标准输出和错误输出
        full_log = (res.stdout or "") + (res.stderr or "")
        return res.returncode == 0, full_log
    except Exception as e:
        return False, str(e)

def run_coder(task_id, role, auto_confirm=False, debug=False):
    # 1. 加载上下文
    task_content = load_file(TASK_FILES[role])
    pattern = rf"- \[(.*?)\] ({task_id}: (.*?) \| (.*?) \| Ref: (.*?) \| Feedback: (.*))"
    match = re.search(pattern, task_content)
    if not match: 
        return print(colored(f"❌ Task {task_id} not found", "red"))
    
    status, _, title, detail, ref, feedback = match.groups()
    design = load_file(DESIGN_FILES[role])
    prd = load_file(PRD_FILE)
    prompt_tmpl = load_file("agents/prompts/coder_prompt.md")

    attempt = 0
    current_fb = feedback
    
    while attempt < MAX_RETRIES:
        attempt += 1
        print(colored(f"\n🛠️ Attempt {attempt}/{MAX_RETRIES} for {task_id}", "cyan", attrs=["bold"]))
        
        # 变量替换
        user_input = (prompt_tmpl
                      .replace("{{task_id}}", task_id)
                      .replace("{{task_title}}", title)
                      .replace("{{task_detail}}", detail)
                      .replace("{{design_content}}", design)
                      .replace("{{prd_content}}", prd)
                      .replace("{{feedback}}", current_fb))
        
        system_msg = "You are a Senior Engineer."

        # --- DEBUG 模式：打印发送给大模型的内容 ---
        if debug:
            print(colored("\n" + "="*30 + " DEBUG: LLM INPUT " + "="*30, "magenta"))
            print(colored(f"System Prompt:\n{system_msg}", "white"))
            print(colored(f"\nUser Input:\n{user_input}", "white"))
            print(colored("="*78 + "\n", "magenta"))

        # 调用 Coder 模型
        output = call_llm_for_agent("coder", system_msg, user_input)
        
        # 2. 交互确认
        if not auto_confirm:
            print(colored("\n--- AI Proposed Plan ---", "yellow"))
            print(output.split("### FILE:")[0]) # 预览计划
            confirm = input(colored("\nApply changes? (y/n/skip): ", "green")).lower()
            if confirm == 'skip': return
            if confirm != 'y': continue
        
        # 3. 执行应用
        parse_and_apply(output)
        
        # 4. 自动化测试 (TDD)
        success, log = run_tests(role)

        if success:
            print(colored(f"✅ Tests Passed for {task_id}!", "green", attrs=["bold"]))
            update_task_status(task_id, role, "review", "None")
            return
        else:
            print(colored(f"❌ Tests Failed.", "red"))
            # 【截取前500行/字符逻辑】
            # 使用 splitlines 获取行，防止单行过长，并取前 500 个字符
            clean_log = " ".join(log.splitlines())[:500] 
            current_fb = f"ERROR LOG: {clean_log}..."
            
            if attempt == MAX_RETRIES:
                print(colored("🛑 Max retries reached.", "red", attrs=["bold"]))
                update_task_status(task_id, role, "todo", current_fb)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-t", "--task", required=True)
    parser.add_argument("-r", "--role", choices=["be", "fe"], required=True)
    parser.add_argument("-y", "--yes", action="store_true", help="自动确认")
    parser.add_argument("-d", "--debug", action="store_true", help="打印大模型输入") # 新增 debug 选项
    
    args = parser.parse_args()
    run_coder(args.task, args.role, auto_confirm=args.yes, debug=args.debug)