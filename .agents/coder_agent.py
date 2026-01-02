import sys
import os
import json
import re
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

def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""

def parse_multiple_files(text):
    """
    解析格式：
    ### FILE: path/to/file1.py
    code...
    ### FILE: path/to/file2.py
    code...
    """
    # 正则匹配 ### FILE: 后面跟着非换行符的内容
    pattern = re.compile(r'### FILE:\s*(.+)')
    parts = pattern.split(text)
    
    # split 后第一个元素通常是空或无关文本，后面是 (文件名, 内容, 文件名, 内容...)
    files = {}
    if len(parts) < 2:
        return files # 解析失败

    # 从索引 1 开始，每次跳 2 步 (1是文件名, 2是内容)
    for i in range(1, len(parts), 2):
        path = parts[i].strip()
        content = parts[i+1].strip()
        # 清理可能残留的 ```
        if content.startswith("```"): content = content.split("\n", 1)[1]
        if content.endswith("```"): content = content.rsplit("```", 1)[0]
        files[path] = content.strip()
        
    return files

def main():
    print("👷 Coder Agent (Multi-File) is ready...")

    if not TASKS_FILE.exists():
        print("❌ No tasks.json found.")
        return

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

    # 准备上下文
    prd_content = load_text(PRD_FILE)
    prompt_template = load_text(PROMPT_FILE)
    
    # 获取 Reviewer 的反馈 (如果是被打回的任务)
    feedback = target_task.get("feedback", "")
    feedback_section = ""
    if feedback:
        print(colored(f"⚠️  Addressing Feedback: {feedback}", "magenta"))
        feedback_section = f"\n=== REVIEWER FEEDBACK (FIX THIS) ===\n{feedback}\n"

    # 填充 Prompt
    user_prompt = prompt_template.replace("{{task_type}}", target_task.get("type", "general")) \
                                 .replace("{{task_title}}", target_task["title"]) \
                                 .replace("{{task_desc}}", target_task["description"]) \
                                 .replace("{{acceptance_criteria}}", target_task.get("acceptance_criteria", "")) \
                                 .replace("{{prd_ref}}", target_task.get("prd_ref", "General")) \
                                 .replace("{{prd_content}}", prd_content) \
                                 .replace("{{existing_content}}", "") # 简化：暂不读取旧内容，避免 token 爆炸

    # 调用 Codex
    raw_output = call_llm("You are a Senior Engineer.", user_prompt)
    
    # 解析多文件
    generated_files = parse_multiple_files(raw_output)
    
    if not generated_files:
        print(colored("❌ Failed to parse files from LLM output.", "red"))
        print("Raw Output snippet:", raw_output[:200])
        return

    saved_paths = []
    for rel_path, content in generated_files.items():
        full_path = PROJECT_ROOT / rel_path
        full_path.parent.mkdir(parents=True, exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(colored(f"💾 Saved: {rel_path}", "yellow"))
        saved_paths.append(rel_path)

    # 更新任务
    # 把所有生成的文件路径都记下来
    target_task["file_path"] = ", ".join(saved_paths) 
    target_task["status"] = "review" # 移交给审查者
    
    with open(TASKS_FILE, "w", encoding="utf-8") as f:
        json.dump(tasks, f, indent=2, ensure_ascii=False)
    
    print("✅ Task moved to 'review'.")

if __name__ == "__main__":
    main()