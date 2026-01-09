import os
import argparse
from termcolor import colored
from agents.lib.llm import call_llm_for_agent
from agents.config import PRD_FILE

# --- 路径常量定义 ---
# 建议保持此结构，以便前后端开发人员清晰定位
DESIGN_DIR = "docs/design"
BE_DESIGN_PATH = os.path.join(DESIGN_DIR, "backend.md")
FE_DESIGN_PATH = os.path.join(DESIGN_DIR, "frontend.md")
BE_TASKS_PATH = "docs/TASKS_BE.md"
FE_TASKS_PATH = "docs/TASKS_FE.md"

def load_file(path):
    """读取文件内容，不存在则返回空字符串"""
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    return ""

def save_file(path, content):
    """保存内容到文件，自动创建中间目录"""
    if not content:
        return
    os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def run_pipeline(actions, scope):
    """
    核心执行流水线
    actions: {"gd": bool (Generate Design), "st": bool (Sync/Audit Tasks)}
    scope: {"be": bool, "fe": bool}
    """
    print(colored("="*60, "blue"))
    print(colored(f"🚀 Architect Agent Pipeline | Actions: {actions} | Scope: {scope}", "blue", attrs=["bold"]))
    print(colored("="*60, "blue"))

    prd_content = load_file(PRD_FILE)
    if not prd_content:
        print(colored(f"❌ 错误: 找不到 PRD 文件，路径: {PRD_FILE}", "red"))
        return

    # --- 第一阶段：生成/更新设计文档 (Design Phase) ---
    if actions["gd"]:
        # 1. 后端设计
        if scope["be"]:
            print(colored("\n[Designer] 🧠 正在规划后端架构 (Java/Spring Boot 3)...", "cyan"))
            prompt = load_file("agents/prompts/architect_backend_prompt.md")
            existing_be = load_file(BE_DESIGN_PATH)
            # 传入 PRD 和现有设计，支持 AI 进行增量更新
            res = call_llm_for_agent("architect_backend", prompt, 
                                    f"PRD_SOURCE:\n{prd_content}\n\n"
                                    f"EXISTING_DESIGN_DOC:\n{existing_be}")
            save_file(BE_DESIGN_PATH, res)
            print(colored(f"✅ 后端设计已保存: {BE_DESIGN_PATH}", "green"))

        # 2. 前端设计
        if scope["fe"]:
            print(colored("\n[Designer] 🎨 正在规划前端架构 (UniApp/Vue 3)...", "cyan"))
            prompt = load_file("agents/prompts/architect_frontend_prompt.md")
            
            be_context = load_file(BE_DESIGN_PATH)
            existing_fe = load_file(FE_DESIGN_PATH)
            
            # --- 核心修改：注入 API 强对齐指令 ---
            frontend_input = f"PRD_SOURCE:\n{prd_content}\n\n"
            
            if be_context:
                frontend_input += (
                    "!!! IMPORTANT: BACKEND_API_REFERENCE DETECTED !!!\n"
                    "你必须严格遵守以下后端定义的 API 契约（路径、方法、字段名、数据类型）。\n"
                    "严禁在前端设计中自行修改或重命名后端已定义的字段。\n"
                    "如果后端设计中有不满足前端需求的地方，请在前端设计的 'Implementation Notes' 中记录冲突，而不是私自修改。\n\n"
                    f"BACKEND_API_REFERENCE:\n{be_context}\n\n"
                )
            
            frontend_input += f"EXISTING_DESIGN_DOC:\n{existing_fe}"
            # ------------------------------------

            res = call_llm_for_agent("architect_frontend", prompt, frontend_input)
            save_file(FE_DESIGN_PATH, res)
            print(colored(f"✅ 前端设计已保存: {FE_DESIGN_PATH}", "green"))
            
        print(colored("\n💡 设计生成完毕。请手动审核 docs/design/ 中的内容，如有修改，Task Broker 将在下一步自动感知。", "yellow"))

    # --- 第二阶段：同步与审计任务 (Task Broker & Audit Phase) ---
    if actions["st"]:
        broker_prompt = load_file("agents/prompts/task_broker_prompt.md")

        # 1. 后端任务审计与同步
        if scope["be"]:
            print(colored("\n[Broker] 📋 正在审计并同步后端任务 (Audit Mode)...", "magenta"))
            design = load_file(BE_DESIGN_PATH)
            current_tasks = load_file(BE_TASKS_PATH)
            # 核心：将设计文档和现有任务列表同时传给 AI 进行对比去重和孤儿检测
            res = call_llm_for_agent("task_broker", broker_prompt, 
                                    f"ROLE: BACKEND\n"
                                    f"DESIGN_DOC_REFERENCE:\n{design}\n\n"
                                    f"CURRENT_TASKS_POOL:\n{current_tasks}")
            save_file(BE_TASKS_PATH, res)
            print(colored(f"✅ 后端任务池同步完成: {BE_TASKS_PATH}", "green"))

        # 2. 前端任务审计与同步
        if scope["fe"]:
            print(colored("\n[Broker] 📋 正在审计并同步前端任务 (Audit Mode)...", "magenta"))
            design = load_file(FE_DESIGN_PATH)
            current_tasks = load_file(FE_TASKS_PATH)
            res = call_llm_for_agent("task_broker", broker_prompt, 
                                    f"ROLE: FRONTEND\n"
                                    f"DESIGN_DOC_REFERENCE:\n{design}\n\n"
                                    f"CURRENT_TASKS_POOL:\n{current_tasks}")
            save_file(FE_TASKS_PATH, res)
            print(colored(f"✅ 前端任务池同步完成: {FE_TASKS_PATH}", "green"))

    print(colored("\n" + "="*60, "blue"))
    print(colored("🏁 Architect Agent 任务执行完毕。", "blue", attrs=["bold"]))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="铭宇 Agent 协作平台 - Architect 模块",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例 (Example Commands):
  1. 生成后端设计:        python -m agents.architect_agent -gd -be
  2. 生成前后端全部设计:  python -m agents.architect_agent -gd -all
  3. 审计并同步后端任务:  python -m agents.architect_agent -st -be
  4. 一键更新所有设计与任务: python -m agents.architect_agent -gd -st -all
        """
    )

    # 动作 (Action Arguments)
    parser.add_argument("-gd", "--generate_design", action="store_true", help="生成或更新设计文档 (Design Phase)")
    parser.add_argument("-st", "--sync_tasks", action="store_true", help="同步并审计任务列表 (Sync & Audit Phase)")

    # 范围 (Scope Arguments)
    parser.add_argument("-be", "--backend", action="store_true", help="仅针对后端 (Backend)")
    parser.add_argument("-fe", "--frontend", action="store_true", help="仅针对前端 (Frontend)")
    parser.add_argument("-all", "--all_scope", action="store_true", help="前后端全选")

    args = parser.parse_args()

    # 将参数解析为内部逻辑开关
    active_actions = {
        "gd": args.generate_design,
        "st": args.sync_tasks
    }
    active_scope = {
        "be": args.backend or args.all_scope,
        "fe": args.frontend or args.all_scope
    }

    # 参数合法性检查
    if not any(active_actions.values()):
        parser.print_help()
    elif not any(active_scope.values()):
        print(colored("\n❌ 错误: 请指定执行范围。使用 -be, -fe 或 -all", "red"))
    else:
        run_pipeline(active_actions, active_scope)