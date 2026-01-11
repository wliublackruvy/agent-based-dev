import os
import re
import sys
import subprocess
import argparse
from pathlib import Path
from termcolor import colored

# 确保可以导入项目中的其他模块
sys.path.append(os.getcwd())
from agents.lib.llm import call_llm_for_agent
from agents.config import DOCS_DIR, PRD_FILE

# --- 路径常量定义 ---
TASK_FILES = {
    "be": os.path.join(DOCS_DIR, "TASKS_BE.md"),
    "fe": os.path.join(DOCS_DIR, "TASKS_FE.md")
}
DESIGN_FILES = {
    "be": os.path.join(DOCS_DIR, "design/backend.md"),
    "fe": os.path.join(DOCS_DIR, "design/frontend.md")
}

MAX_RETRIES = 3 # 最大自愈重试次数

def load_file(path):
    return open(path, 'r', encoding='utf-8').read() if os.path.exists(path) else ""

def save_file(path, content):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

# --- 任务状态管理逻辑 ---

def get_task_info(task_id, role):
    """从 Markdown 文件中精确提取任务状态、标题和反馈"""
    content = load_file(TASK_FILES[role])
    # 匹配格式: - [状态] Task-ID: [模块] 标题 | Ref: 章节 | Feedback: 内容
    pattern = rf"- \[(.*?)\] {task_id}: (.*?) \| Ref: (.*?) \| Feedback: (.*)"
    match = re.search(pattern, content)
    if match:
        return {
            "status": match.group(1),
            "title": match.group(2),
            "ref": match.group(3),
            "feedback": match.group(4)
        }
    return None

def update_task_in_markdown(task_id, role, new_status, feedback="None"):
    """将更新后的状态和反馈写回 Markdown 任务列表"""
    path = TASK_FILES[role]
    content = load_file(path)
    pattern = rf"(- \[(.*?)\] {task_id}: (.*?) \| Feedback: )(.*)"
    replacement = rf"- [{new_status}] {task_id}: \3 | Feedback: {feedback}"
    
    new_content = re.sub(pattern, replacement, content)
    save_file(path, new_content)

# --- 文件操作逻辑 ---

def apply_file_changes(llm_output):
    """解析 ### FILE: 和 ### DELETE: 指令并执行磁盘操作"""
    # 1. 处理新增/修改
    file_blocks = re.findall(r"### FILE:\s*(.*?)\n(.*?)(?=\n### FILE:|\n### DELETE:|$)", llm_output, re.DOTALL)
    for path_str, content in file_blocks:
        save_file(path_str.strip(), content.strip())
        print(colored(f"💾 Applied: {path_str.strip()}", "blue"))
    
    # 2. 处理删除
    delete_blocks = re.findall(r"### DELETE:\s*(.*)", llm_output)
    for path_str in delete_blocks:
        p = path_str.strip()
        if os.path.exists(p):
            os.remove(p)
            print(colored(f"🗑️ Deleted: {p}", "red"))

# --- 测试运行逻辑 ---

def run_unit_tests(role):
    """
    运行对应平台的测试命令
    返回: (是否通过, 错误日志)
    """
    print(colored("🧪 Running Unit Tests...", "magenta"))
    try:
        if role == "be":
            # Maven 测试命令示例
            cmd = ["mvn", "test", "-DfailIfNoTests=false"]
        else:
            # UniApp/Vue 测试命令示例
            cmd = ["npm", "run", "test:unit"]
        
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        return result.returncode == 0, result.stdout + result.stderr
    except Exception as e:
        return False, str(e)

# --- 核心执行流程 ---

def run_coder_pipeline(task_id, role):
    print(colored(f"\n🚀 Coder Agent Activation: {task_id} ({role.upper()})", "blue", attrs=["bold"]))
    
    # 1. 加载上下文
    task_info = get_task_info(task_id, role)
    if not task_info:
        print(colored(f"❌ Error: Task {task_id} not found.", "red"))
        return

    design_doc = load_file(DESIGN_FILES[role])
    prd_content = load_file(PRD_FILE)
    coder_prompt = load_file("agents/prompts/coder_prompt.md")

    # 2. 初始尝试
    attempt = 0
    current_feedback = task_info['feedback']
    
    while attempt < MAX_RETRIES:
        attempt += 1
        mode = "REPAIR" if current_feedback != "None" else "INITIAL"
        print(colored(f"\n[Attempt {attempt}/{MAX_RETRIES}] {mode} Mode...", "cyan"))

        # 构建输入变量
        user_input = f"""
        TASK_ID: {task_id}
        TITLE: {task_info['title']}
        DESIGN: {design_doc}
        PRD: {prd_content}
        FEEDBACK: {current_feedback}
        """

        # 调用 LLM (模型根据 config.py 自动路由)
        llm_output = call_llm_for_agent("coder", coder_prompt, user_input)
        
        # 预检：展示执行计划（可选，如果 LLM 输出包含 Plan）
        print(colored("\n--- AI Proposed Changes ---", "yellow"))
        # print(llm_output) # 调试用

        # 3. 询问人类确认 (Safety Gate)
        confirm = input(colored(f"\nApply changes for {task_id}? (y/n/skip): ", "green"))
        if confirm.lower() == 'skip': break
        if confirm.lower() != 'y': continue

        # 4. 应用修改并测试
        apply_file_changes(llm_output)
        success, log = run_unit_tests(role)

        if success:
            print(colored(f"✅ Success! Task {task_id} passed unit tests.", "green"))
            update_task_status = "review"
            update_task_in_markdown(task_id, role, "review", "None")
            return True
        else:
            print(colored(f"❌ Failure on attempt {attempt}.", "red"))
            # 记录失败日志作为下一次重试的 Feedback
            current_feedback = f"Test Failure (Attempt {attempt}): {log[-500:]}"
            if attempt == MAX_RETRIES:
                print(colored("🛑 Max retries reached. Moving to Manual TODO.", "red"))
                update_task_in_markdown(task_id, role, "todo", current_feedback)

    return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-t", "--task", required=True, help="Task ID (e.g., Task-BE-001)")
    parser.add_argument("-r", "--role", choices=["be", "fe"], required=True, help="Role (be/fe)")
    args = parser.parse_args()
    
    run_coder_pipeline(args.task, args.role)